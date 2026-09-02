
addFeature("cmdSampleExtension", new LazyFunction("features/command.mjs", "getView",
    [
        "cmdSampleExtensionView",
        new CommandDef("Sample: [ java extension command ]", "runext", "sample-command")
            .setOption("args", true)
    ]
), { topic: 'Server Commands', item: 'Sample: Java command' });