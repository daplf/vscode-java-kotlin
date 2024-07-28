# VSCode Extension for Java + Kotlin

A VSCode extension that enhances [vscode-java](https://github.com/redhat-developer/vscode-java) and [vscode-kotlin](https://github.com/fwcd/vscode-kotlin) with Java + Kotlin interoperability. This uses a JDT LS extension with a delegate command handler that can work together with the Kotlin Language Server, in order for Java code to have access to Kotlin code.

To use, make sure to have the Kotlin extension installed and to have code generation enabled in your VSCode settings:

```json
{
  "kotlin.codegen.enabled": true
}
```

**Disclaimer**: This is very experimental, but it seems to work for small maven and gradle projects at least.

## Features

- Java + Kotlin project compilation
- Kotlin symbols available in completions when writing Java code

## Development

For now, to set this up, you need to run the `build.sh` script in this directory to package the JDT LS extension. Afterwards, you should run `npm install` to install the extension dependencies.

To debug, you can use F5 on VSCode, as with any other extension. To package the extension you can use `vsce package`.
