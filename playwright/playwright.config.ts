import { defineConfig, devices } from '@playwright/test';

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './tests',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /* Build and start a fresh Quarkus instance before every test run so the
     in-memory H2 database is always re-seeded from scratch.
     reuseExistingServer: false kills any running instance first.
     `npm run build` emits the browser bundle (js/hono/hx-hono.js) that renders
     the hono templates client-side — plain JDK 21+, no GraalVM. */
  webServer: {
    command: `cd .. && npm run build && mvn package -q -DskipTests && java -jar target/quarkus-app/quarkus-run.jar`,
    url: 'http://localhost:8080',
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
