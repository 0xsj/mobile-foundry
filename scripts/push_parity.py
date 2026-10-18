#!/usr/bin/env python3
"""Push foundation conformance with injected gateways; no cloud keys or sends."""
from pathlib import Path
import tempfile
import uuid
from backend_parity import ROOT, owned_server, run
from identity_parity import call, refusal

def main():
    run(["go","test","-race","./parity","-run","^TestSharedPush","-count=1"],ROOT/"backend/go")
    run(["go","test","-race","./tests","-run","^TestPush(Providers|CapabilityAndRevocationBeforeCleanup)$","-count=1"],ROOT/"backend/go")
    run(["cargo","test","--locked","-p","identity","--test","eligibility"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","notifications","--test","push","--test","push_access"],ROOT/"backend/rust")
    run(["cargo","test","--locked","-p","api-server","--test","push_dispatch"],ROOT/"backend/rust")
    with tempfile.TemporaryDirectory(prefix="mobile-push-parity-") as temporary:
        owned=Path(temporary);go=owned/"go-api"
        run(["go","build","-o",str(go),"./cmd/server"],ROOT/"backend/go")
        run(["cargo","build","--locked","-p","api-server"],ROOT/"backend/rust")
        for name,binary in [("Go",go),("Rust",ROOT/"backend/rust/target/debug/api-server")]:
            for auth in ["deny","identity"]:
                log=owned/(name+"-"+auth+".log")
                # owned_server replaces inherited provider/job settings entirely.
                with owned_server(binary,log,auth) as port:
                    refusal(call(port,"/v1/installations/"+str(uuid.uuid4())+"/push-preview",body={"preview_id":str(uuid.uuid4())},
                                 headers=[("X-Installation-Key","PRIVATE-unused-proof")]),404,"http.route_not_found")
                assert "PRIVATE-unused-proof" not in log.read_text(),"private header leaked"
            print(name+": default push route remains absent, private logs and clean shutdown passed",flush=True)
    print("Push: 21 attempt, 20 request, 14 response and 6 session cases; injected gateway acceptance only",flush=True)

if __name__=="__main__":main()
