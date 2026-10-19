"""Explicit Cargo source closure and owned API identity, without invoking Cargo at init."""
from pathlib import Path
import re
import tomllib
from .profiles import ProfileError, admitted_path, relative_path

MODULES = ["health", "identity", "account", "audit", "notes", "files", "notifications", "realtime"]

def admit_package(value, profile, root):
    if not isinstance(value,str) or len(value)>64 or not re.fullmatch(r"[a-z][a-z0-9]*(?:-[a-z0-9]+)*",value) or value.upper() in {"CON","PRN","AUX","NUL",*(f"COM{i}" for i in range(1,10)),*(f"LPT{i}" for i in range(1,10))}:
        raise ProfileError("Rust package must use 1–64 lowercase letters/digits with single internal hyphens, starting with a letter.")
    for member in profile["workspaceMembers"]:
        destination="backend/rust/"+member+"/Cargo.toml"
        source=next((o["source"] for o in profile["overlays"] if o["destination"]==destination),destination)
        package=tomllib.loads(admitted_path(root,source).read_text())["package"]["name"]
        if value==package and package!=profile["sourcePackage"]:
            raise ProfileError("Rust API package collides with a reusable workspace package.")
    lock_source=next((o["source"] for o in profile["overlays"] if o["destination"]=="backend/rust/Cargo.lock"),"backend/rust/Cargo.lock")
    locked=tomllib.loads(admitted_path(root,lock_source).read_text())["package"]
    if value != profile["sourcePackage"] and any(p["name"]==value for p in locked):
        raise ProfileError("Rust API package collides with a locked dependency package.")
    return value

def validate_inventory(profile, root):
    errors=[]
    try:
        if profile.get("schemaVersion")!=1 or profile.get("status") not in {"candidate","standalone"} or profile.get("directory")!="backend/rust" or profile.get("modules")!=MODULES:
            raise ProfileError("Expected a schemaVersion 1 Rust backend inventory with the implemented modules.")
        lists=[profile[k] for k in ("sources","sharedSources","requiredFiles","exclude","workspaceMembers")]
        if any(not isinstance(xs,list) or not all(isinstance(x,str) for x in xs) or len(xs)!=len(set(xs)) for xs in lists):
            raise ProfileError("Rust inventory lists must contain unique strings.")
        if any(not p.startswith("backend/rust/") for p in profile["sources"]):
            raise ProfileError("Rust backend sources must stay within backend/rust.")
        for p in profile["sources"]+profile["sharedSources"]:admitted_path(root,p)
        destinations=set()
        for overlay in profile["overlays"]:
            source=admitted_path(root,overlay["source"]);destination=str(relative_path(overlay["destination"]))
            if not source.is_file() or not destination.startswith("backend/rust/") or destination in destinations:
                raise ProfileError("Rust overlays require unique backend/rust destinations and regular sources.")
            destinations.add(destination)
        # Inspect exactly the effective files generation copies, including exclusions.
        from .generation import ProductInputs, plan_backend_sources
        from .backends import BackendSelection
        files=plan_backend_sources(root,BackendSelection(profile,""),ProductInputs.admit("inventory","none"))
        selected={f.relative:f.data for f in files}
        def manifest(path):
            if path not in selected:raise ProfileError("Unselected Cargo manifest: "+path)
            return tomllib.loads(selected[path].decode())
        workspace=manifest("backend/rust/Cargo.toml")["workspace"]
        if manifest("backend/rust/Cargo.toml").get("patch") or manifest("backend/rust/Cargo.toml").get("replace"):
            raise ProfileError("Rust registry patch/replace overrides require separate extraction support.")
        if workspace["members"]!=profile["workspaceMembers"] or workspace["package"]["rust-version"]!=profile["rustVersion"]:
            raise ProfileError("Rust workspace/toolchain inventory drift.")
        names=set()
        for member in workspace["members"]:
            relative="backend/rust/"+str(relative_path(member))+"/Cargo.toml"
            value=manifest(relative);name=value["package"]["name"]
            if name in names:raise ProfileError("Duplicate Cargo package identity.")
            names.add(name)
        server=manifest("backend/rust/cmd/server/Cargo.toml")
        if server["package"]["name"]!=profile["sourcePackage"] or server.get("bin")!=[{"name":"api-server","path":"src/main.rs"}]:
            raise ProfileError("Rust source API package/binary inventory drift.")
        locked=tomllib.loads(selected["backend/rust/Cargo.lock"].decode())
        if len([p for p in locked["package"] if p["name"]==profile["sourcePackage"] and "source" not in p])!=1:
            raise ProfileError("Rust lock must retain the selected API package.")
        # All local dependencies must resolve inside the selected backend workspace.
        for relative,data in selected.items():
            if relative.endswith("Cargo.toml"):
                value=tomllib.loads(data.decode())
                def dependencies(tree):
                    if isinstance(tree,dict):
                        for key,item in tree.items():
                            if key in {"dependencies","dev-dependencies","build-dependencies"}:
                                for dependency in item.values():
                                    if isinstance(dependency,dict) and "path" in dependency:
                                        target=(root/Path(relative).parent/dependency["path"]/"Cargo.toml").resolve()
                                        if not target.is_relative_to(root/"backend/rust") or str(target.relative_to(root)) not in selected:
                                            errors.append(relative+": unselected/escaping Cargo path dependency")
                            else:dependencies(item)
                dependencies(value)
            if relative.endswith(".rs"):
                text=data.decode()
                # Literal includes are the admitted syntax; computed includes need explicit review.
                directives=list(re.finditer(r'\binclude(?:_str|_bytes)?!\s*\(\s*"([^"\n]+)"\s*\)',text))
                if len(directives)!=len(re.findall(r'\binclude(?:_str|_bytes)?!\s*\(',text)):
                    errors.append(relative+": unsupported computed Rust include")
                for included in directives:
                    target=(root/Path(relative).parent/included[1]).resolve()
                    if not target.is_relative_to(root) or str(target.relative_to(root)) not in selected:
                        errors.append(relative+": unselected Rust include "+included[1])
                for included in re.findall(r'#\[path\s*=\s*"([^"\n]+)"\]',text):
                    target=(root/Path(relative).parent/included).resolve()
                    if not target.is_relative_to(root) or str(target.relative_to(root)) not in selected:
                        errors.append(relative+": unselected Rust module path "+included)
    except (KeyError,TypeError,ValueError,AttributeError,OSError) as error:
        errors.append("Invalid Rust backend inventory: "+str(error))
    return errors

def configure(destination, selection, inputs):
    folder=destination/"backend/rust";old=selection.profile["sourcePackage"];new=selection.package
    path=folder/"cmd/server/Cargo.toml"
    count=0
    def package_table(match):
        nonlocal count
        body,matched=re.subn(r'(?m)^name = "'+re.escape(old)+r'"$',lambda _:f'name = "{new}"',match[2])
        count+=matched
        return match[1]+body
    text=re.sub(r'(?ms)(^\[package\]\n)(.*?)(?=^\[|\Z)',package_table,path.read_text())
    if count!=1:raise ProfileError("Expected one owned Rust API package declaration.")
    path.write_text(text)
    # Only the package changes; the explicitly named api-server binary stays stable.
    path=folder/"Cargo.lock";text=path.read_text();blocks=text.split("[[package]]\n");prefix=blocks.pop(0)
    candidates=[b for b in blocks if tomllib.loads(b).get("name")==old and "source" not in tomllib.loads(b)]
    if len(candidates)!=1:raise ProfileError("Expected one owned Rust API lock entry.")
    block=candidates[0];blocks.remove(block)
    block,count=re.subn(r'(?m)^name = "'+re.escape(old)+r'"$',lambda _:f'name = "{new}"',block)
    if count!=1:raise ProfileError("Expected one owned Rust API lock name.")
    position=next((i for i,b in enumerate(blocks) if tomllib.loads(b)["name"]>new),len(blocks));blocks.insert(position,block)
    path.write_text(prefix+"".join("[[package]]\n"+b for b in blocks))
    path=folder/"Makefile";text=path.read_text().replace("-p "+old,"-p "+new)
    marker="PROD_PROJECT ?= mobile-foundry-rust-prod"
    if text.count(marker)!=1:raise ProfileError("Expected one owned Rust production namespace.")
    path.write_text(text.replace(marker,"PROD_PROJECT ?= "+inputs.name+"-prod"))
    for relative in ("infra/production/compose.yaml","infra/production/.env.example"):
        path=folder/relative;path.write_text(path.read_text().replace("mobile-foundry-rust-api:local",inputs.name+"-api:local"))
    path=folder/"infra/local/compose.yaml";text=path.read_text()
    if text.count("name: mobile-foundry-rust")!=1:raise ProfileError("Expected one owned Rust local namespace.")
    path.write_text(text.replace("name: mobile-foundry-rust","name: "+inputs.name+"-api"))
