import js from '@eslint/js';
import { polarionEslintConfig } from '@sbb-polarion/react-sbb-polarion/eslint-config';
import globals from 'globals';

// The shared setup of the SBB Polarion React apps (TypeScript, React hooks, jsx-a11y, Prettier), plus
// this extension's plain JS tooling. src/vendor is reserved for vendored third-party code and is not
// linted, like RSP's src/generic.
export default polarionEslintConfig({
  ignores: ['node', '.vite', 'src/vendor', 'test/expected', 'test/__diff__', 'test/__screenshots__', '.vitest'],
  configs: [
    // Plain JS/ESM (this config, vite.config.js, the docker-test wrapper).
    {
      files: ['**/*.{js,mjs}'],
      extends: [js.configs.recommended],
      languageOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        globals: { ...globals.node },
      },
    },
  ],
});
