
addFeature("cmdSampleBuildProject", new LazyFunction("features/command.mjs", "getView",
    [
        "cmdSampleBuildProjectView",
        new CommandDef("Sample: [ js build script ]", "runjs", "/sample/build-project.mjs"),
        new LazyFunction("features/extender/sample-build-project.ext.mjs", "extendView").setReturnFunctionMode()
    ]
), { topic: 'Server Commands', item: 'Sample: Build script' });