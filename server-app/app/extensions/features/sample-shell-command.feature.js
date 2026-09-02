
addFeature("cmdSampleShellCall", new LazyFunction("features/command.mjs", "getView",
    [
        "cmdSampleShellCallView",
        new CommandDef("Sample: [ js shell command ]", "runjs", "/sample/shell-call.mjs")
            .setOption("args", true)
    ]
), { topic: 'Server Commands', item: 'Sample: JS command' });