# Notifications construction

The backend delivers a private in-app inbox from committed upload facts. Swift and
Kotlin now connect the existing notification components through native repository
ports and app-owned state controllers. The component preview remains a fixture;
the connected panel lives in Go identity and accounts.

Start with [behavior](../../contracts/behavior/notifications.md),
[HTTP](../../contracts/http/notifications-v1.md) and
[the module walkthrough](../../backend/go/notes/modules/internal/notifications/README.md).

## Run and inspect

Run AUTH_MODE=identity make dev in backend/go, sign in and select/bootstrap an
account through the current catalog or API. An upload completed through Account
files delivers files.upload_ready to that uploader. Use the routes below with
the same account and current access token. Upload request alone creates no notice.
Delivery is asynchronous; refresh until the producer worker has delivered it.

Runnable (from backend/go, after exporting ACCOUNT_ID and ACCESS_TOKEN):

```sh
curl --fail -H "Authorization: Bearer $ACCESS_TOKEN" \
  "http://127.0.0.1:8080/v1/accounts/$ACCOUNT_ID/notifications"
```

For durable data, run make db-up migrate and STORAGE=postgres AUTH_MODE=identity
make dev. Retain the database and Files directory together. The new Notifications
migration is required even when upgrading an existing local stack. API startup
will report a schema failure until explicit migration succeeds.

The standalone Go CLI selection includes Notifications. Generated native screens
remain separately composed. System push is not selected here. APNs/FCM need
installation ownership, credential/configuration and a
durable outbound queue, rather than a provider call inside event consumption.

## Native walkthrough

Open Catalog → Go identity and accounts, connect to the API, sign in and select an
account. Below Account files, Account inbox shows the server unread count and
media preference. Choose a gallery image in Account files, wait for completion,
then tap Refresh inbox. Open an upload-ready row or Mark read to acknowledge it.
Unread only filters the list; Load more advances its scoped cursor. Opening a
notice shows its file ID without implying that the file still exists.

Media notifications changes the account/principal preference. An unconfirmed
mutation keeps its last confirmed display value and asks for refresh before
another change. Renewal/account switching cancels old work and reloads through a
new exact-lease client; it does not replay a setting. The view retains at most
200 rows. There is no background polling, push prompt or local optimistic badge.

Read [the reconciliation pattern](../../notes/patterns/inbox-refresh-and-setting-uncertainty.md),
then the Swift/Kotlin Services and catalog walkthroughs linked there.

## Acceptance

Provider conformance covers twelve concurrent deliveries of one source, conflicting
source input, stable first-read time, account/principal isolation, arrival-order
pagination, suppressed replay, preference/delivery races and cancellation.
PostgreSQL additionally forces failures in item, inbox and scope writes and checks
that a retry allocates sequence one with no partial marker. HTTP exercises real
Identity/Account/Files composition and asynchronous notification delivery.

The disposable process test blocks Notifications while the upload completes,
kills the API, removes the temporary test-only constraint and checks recovery.
Another restart verifies the single item, read acknowledgement and preference.
The generated-runner probe covers authenticated upload → inbox → read → settings
from an independently copied API. Source and generated execution results are
recorded in [starter readiness](../STARTER-READINESS.md).

Native service tests execute the shared page-admission corpus, including rejected
normalized calendar/time values. Two owner tests pass on each platform; iOS result
counts are confirmed from its simulator bundle and Android from its unit XML. Both
catalogs build. The owned Go runner observes real URLSession/OkHttp upload delivery,
read/preference acknowledgement loss and account isolation. These are host HTTP
plus app-state checks, not automated inbox gestures against the API.

Runnable (repository root, after installing the native toolchains):

```sh
make go-native-test
```

Runnable (repository root):

```sh
make go-check go-test
make -C backend/go smoke
POSTGRES_TEST_MODE=local POSTGRES_BIN_DIR=/opt/homebrew/bin make -C backend/go integration-test
make cli-test starter-check notes-check
```

The local database command requires PostgreSQL binaries. Default integration-test
uses a disposable Docker database. Both modes own their data; neither resets a
retained development volume. These checks do not establish cloud push, native
inbox gestures, production retention or network failover behavior.
