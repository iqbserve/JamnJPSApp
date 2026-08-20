/* Authored by iqbserve.de */

import { echo, sh, workspacePath, isOnUnix } from "tools.mjs";

/**
 * A playful "JS build script" example to build the JamnJPSApp project 
 * with git and maven in the workspace folder.
 *  
 * REQUIRES:
 *  - a callable git and a maven >= v3.6 installation
 * 
 */
let projectName = "JamnJPSApp"
let projectGitUrl = `https://github.com/iqbserve/${projectName}.git`;
let workspace = workspacePath();
let wsLocalMvnRepo = workspacePath(".m2ws");

function buildProject() {

	echo(`Start build project [${projectName}] from [${projectGitUrl}]`);

	//clear workspace dir
	let cmd = isOnUnix() ? `rm -rf ${projectName}` : `rd /s /q ${projectName}`;
	sh(cmd, workspace);

	//clone git project
	sh(`git clone --verbose ${projectGitUrl}`, workspace);

	//call maven install
	//using a workspace local .m2 repo and -B for batch mode supressing colored output
	cmd = `mvn -B "-Dmaven.repo.local=${wsLocalMvnRepo}" install`;
	echo(cmd);
	sh(cmd, workspacePath(`${projectName}/server-app`));

}

buildProject();
