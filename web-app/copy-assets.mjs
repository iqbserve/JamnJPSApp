import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const srcDir = 'src';
const destDir = '../server-app/app/http';

const copyRecursiveSync = (src, dest) => {
    const exists = fs.existsSync(src);
    const stats = exists && fs.statSync(src);
    const isDirectory = exists && stats.isDirectory();

    if (isDirectory) {
        fs.mkdirSync(dest, { recursive: true });
        fs.readdirSync(src).forEach((childItemName) => {
            copyRecursiveSync(path.join(src, childItemName), path.join(dest, childItemName));
        });
    } else {
        // Only copy non-MTS/TS files that are not in the app directory (tsc handles the app directory)
        // Actually, we can copy everything except .ts files. tsc will overwrite the .js files from app/ if it compiles them.
        // To be safe, let's skip the 'app' directory entirely since tsc handles it.
        const normalizedSrc = src.replace(/\\/g, '/');
        if (!normalizedSrc.startsWith('src/app/') && !(normalizedSrc.endsWith('.mts') || normalizedSrc.endsWith('.ts'))) {
            fs.copyFileSync(src, dest);
            //console.log(`cp: ${src} -> ${dest}`);
        }
    }
};

// process.argv.forEach(function (val, index, array) {
//     console.log(index + ': ' + val);
// });

if (process.argv?.[2] === 'cp') {
    copyRecursiveSync(srcDir, destDir);
    console.log(`Assets copied to [${destDir}].\n`);
} else {
    console.log('NO copy\n');
}
