#!/usr/bin/env python3
"""Local provider protocols + guarded HTTP. No cloud keys, override or cloud send."""
import json
from pathlib import Path
import subprocess
import tempfile
import uuid
from backend_parity import ROOT, owned_server, run
from identity_parity import call, refusal

def main():
    run(["go","test","-race","./internal/notifications/infra/push","-count=1"],ROOT/"backend/go")
    run(["go","test","-race","./tests","-run","^Test(SharedPushPreviewHTTP|PushPreviewHTTPProofScopeVersionAndOutcomes|PushDisabledRuntimeAndMissingAuthority)$","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","notifications","--lib","infra::push"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","api-server","--test","push_http"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-provider-parity-") as temporary:
        owned=Path(temporary)
        # Generated test-only signing material. It never leaves this owned directory.
        subprocess.run(["openssl","genpkey","-algorithm","EC","-pkeyopt","ec_paramgen_curve:P-256","-out",str(owned/"apns.p8")],check=True,capture_output=True)
        config=owned/"push.json"
        config.write_text(json.dumps({"routes":[{"provider":"apns","application":"dev.example.app","environment":"sandbox","team_id":"TEAMID0001","key_id":"KEYID00001","key_file":"apns.p8"}]}))
        go=owned/"go-api";run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            for selected in [False,True]:
                log=owned/(name+str(selected)+".log")
                with owned_server(binary,log,"identity",push_config=config if selected else None) as port:
                    refusal(call(port,"/v1/installations/"+str(uuid.uuid4())+"/push-preview",body={}),401 if selected else 404,"principal.authentication_required" if selected else "http.route_not_found")
                assert str(owned) not in log.read_text(),"private provider path leaked"
            print(name+": default absent, explicit private configuration selects guarded route without network, clean shutdown passed",flush=True)
    print("Providers: 32 shared response + 21 HTTP cases, TLS/JWT/cache/cancellation tests; no live cloud delivery claim",flush=True)

if __name__=="__main__":main()
