'use strict';

import * as vscode from 'vscode';

/** Called when extension is activated */
export async function activate(context: vscode.ExtensionContext) {
    // We call vscode-kotlin's API to fetch the build output path.
    // Note that this code is only executed after vscode-kotlin is setup, so the API should be ready to receive requests.
    const vscodeKotlinApi = vscode.extensions.getExtension('fwcd.kotlin').exports as ExtensionAPI;

    console.log(vscodeKotlinApi)
    // Execute a java workspace command to update the kotlin build output path. This triggers a classpath change on the JDT language server.
    await vscode.commands.executeCommand("java.execute.workspaceCommand", "kotlin.java.setKotlinBuildOutput", vscodeKotlinApi.buildOutputPath);
}

// vscode-kotlin API stub.
interface ExtensionAPI {
    buildOutputPath: string;
}
