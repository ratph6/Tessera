use std::env;
use zed_extension_api::{self as zed, Result};

// The Tessera Zed extension. All the real work lives in the bundled Node language server
// (server/server.js); this wrapper just tells Zed how to launch it.
//
// Zed already runs its own TypeScript server (vtsls) for .ts/.js — that gives ordinary
// IntelliSense once a tsconfig points at the bundled type declarations. Our server runs
// *alongside* it and only contributes completions inside Mixin.inject / AccessWidener string
// arguments, where the TypeScript service has nothing to offer.
struct TesseraExtension;

impl zed::Extension for TesseraExtension {
    fn new() -> Self {
        TesseraExtension
    }

    fn language_server_command(
        &mut self,
        _language_server_id: &zed::LanguageServerId,
        _worktree: &zed::Worktree,
    ) -> Result<zed::Command> {
        // The extension is unpacked into its own working directory; the server ships inside it.
        let server_path = env::current_dir()
            .map_err(|e| format!("could not resolve extension dir: {e}"))?
            .join("server")
            .join("server.js");

        // The WASI sandbox reports Windows paths as /C:/Users/... — Node can't resolve that form,
        // so strip the leading slash before the drive letter (zed-industries/zed#17571).
        let mut server_arg = server_path.to_string_lossy().into_owned();
        if let (zed::Os::Windows, _) = zed::current_platform() {
            if server_arg.len() > 2 && server_arg.starts_with('/') && server_arg.as_bytes()[2] == b':' {
                server_arg.remove(0);
            }
        }

        Ok(zed::Command {
            command: zed::node_binary_path()?,
            args: vec![server_arg, "--stdio".to_string()],
            env: Default::default(),
        })
    }
}

zed::register_extension!(TesseraExtension);
