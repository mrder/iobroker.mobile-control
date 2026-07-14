import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

// Builds directly into admin/ (the folder ioBroker admin actually serves), next to
// jsonConfig.json and the adapter icon. emptyOutDir stays false so those aren't wiped.
export default defineConfig({
    plugins: [react()],
    base: './',
    build: {
        outDir: '../',
        emptyOutDir: false,
        assetsDir: 'tab-assets',
        rollupOptions: {
            input: resolve(__dirname, 'tab.html'),
        },
    },
});
