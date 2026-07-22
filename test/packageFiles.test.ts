import path from 'node:path';
import { validatePackageFiles } from '@iobroker/testing/build/tests/packageFiles';

// Official ioBroker adapter-checker validation (package.json/io-package.json consistency, a
// supported adminUI.config, license fields, ...). Would not have caught the adminUI.tab: "custom"
// bug on its own (it only validates adminUI.config), but it's exactly the kind of check a real
// ioBroker adapter submission goes through and we weren't running it at all.
validatePackageFiles(path.join(__dirname, '..'));
