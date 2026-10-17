package dev.mobilefoundry.http

import dev.mobilefoundry.kernel.*
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Known I/O failures are values; cancellation and unexpected callback defects propagate. */
class OkHttpTransport(client: OkHttpClient = OkHttpClient(), private val onDiagnostic: (Throwable) -> Unit = {}) : HTTPTransport {
    private val client = client.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).cache(null).cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).build()

    override suspend fun send(request: PreparedRequest): AppResult<WireResponse> = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val body = request.body?.toRequestBody((request.headers["content-type"] ?: "application/octet-stream").toMediaType()) ?: when (request.method) {
            HTTPMethod.POST, HTTPMethod.PUT, HTTPMethod.PATCH -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val call = client.newCall(builder.method(request.method.name, body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val error = e
                if (!continuation.isActive) return
                if (call.isCanceled()) { continuation.resumeWithException(CancellationException("The native request was canceled.")); return }
                onDiagnostic(error)
                val meta = FailureMeta("The request could not reach a usable response.", correlationId = request.headers["x-correlation-id"])
                continuation.resume(Outcome.Err(if (error is InterruptedIOException) Failure.Timeout(meta) else Failure.Unavailable(meta)))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    val headers = response.headers.names().associateWith { response.headers.values(it).joinToString(", ") }
                    try {
                        val output = java.io.ByteArrayOutputStream()
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 16 * 1024 * 1024) {
                                    continuation.resume(Outcome.Err(internalFailure("http.response_too_large", "The response exceeds the byte limit.")))
                                    return
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        val bytes = output.toByteArray()
                        continuation.resume(Outcome.Ok(WireResponse(response.code, headers, bytes)))
                    } catch (error: IOException) {
                        if (!continuation.isActive) return
                        onDiagnostic(error)
                        continuation.resume(Outcome.Ok(WireResponse(response.code, headers, null)))
                    } catch (error: Throwable) {
                        // Forward identity across the native callback; do not classify a defect.
                        continuation.resumeWithException(error)
                    }
                }
            }
        })
    }
}
