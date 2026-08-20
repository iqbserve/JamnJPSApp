/* Authored by iqbserve.de */

import { LS, echo, sh, workspacePath, isOnUnix, ArgumentProcessor } from "tools.mjs";

/**
 * A test script to play around.
 * 
 * Hint: this script is also called in a junit test
 * but the test will always be passed
 * 
 */
let helpText = `
Command Help:
Testscript - running a shell command from serverside javascript.
The script first checks if the underlaying platform is Windows or Unix
to then execute the corresponding commands for actual directory and directory content. 

Args: <none>
`;
let workspace = workspacePath();

//for demo 
//useing a callback to consume/pipe script output during execution
let scriptResult = [];
let outputConsumer = (line) => {
	scriptResult.push(line);
};

//a main function
function run() {

	//process args if any
	if (new ArgumentProcessor().process(args, {
		"-h": (val) => { echo(helpText); return -1; }
	})== -1) {
        //for help arg
        //return without processing the script
        return;
    }

	//setup shell commands for the current platform
	let test = isOnUnix() ? { cmd: { pwd: "pwd", ls: "ls -ag" }, os: "unix" } : { cmd: { pwd: "cd", ls: "dir" }, os: "windows" };

	//print out some infos
	echo([
		`Start: [${new Date().toUTCString()}]`,
		`Commands: actual-path: [${test.cmd.pwd}], actual-dir-content: [${test.cmd.ls}], Platform: ${test.os}`,
		`Args: ${args}`,
		""
	].join(LS));

	//run shell commands in workspace dir
	sh(test.cmd.pwd, workspace, outputConsumer);
	sh(test.cmd.ls, workspace, outputConsumer);

	//print out some collected output
	echo("\n\n--------\nDemo collecting output:");
	echo("-first output line: "+scriptResult[0].trim());
	echo("-last output line: "+scriptResult[scriptResult.length-1].trim());

	return "Demo done";
}

run();