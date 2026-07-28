// Karma configuration file, see link for more information
// https://karma-runner.github.io/1.0/config/configuration-file.html

module.exports = function (config) {
  config.set({
    basePath: "",
    frameworks: ["jasmine", "@angular-devkit/build-angular"],
    plugins: [
      require("karma-jasmine"),
      require("karma-chrome-launcher"),
      require("karma-jasmine-html-reporter"),
      require("karma-coverage"),
      require("@angular-devkit/build-angular/plugins/karma"),
    ],
    client: {
      jasmine: {
        // you can add configuration options for Jasmine here
        // the possible options are listed at https://jasmine.github.io/api/edge/Configuration.html
        // for example, you can disable the random execution with `random: false`
        // or set a specific seed with `seed: 4321`
      },
      clearContext: false, // leave Jasmine Spec Runner output visible in browser
    },
    jasmineHtmlReporter: {
      suppressAll: true, // removes the duplicated traces
    },
    coverageReporter: {
  dir: require('path').join(__dirname, './coverage/app'),
  subdir: '.',
  reporters: [
    { type: 'html' },
    { type: 'text-summary' },
    { type: 'lcovonly' },
    { type: 'json-summary' }   // ← add this
  ],
  // SCRUM-214/236: measured 2026-07-28 at statements 67.9% / branches 57.89% /
  // functions 66.17% / lines 70.37% (see docs/ci.md) — floor set a few points below,
  // same method SCRUM-206 used for the backend. karma-coverage fails the `npm run
  // test:ci` step itself when a metric drops below its floor, so frontend-ci.yml
  // needs no separate gating step.
  check: {
    global: {
      statements: 65,
      branches: 55,
      functions: 63,
      lines: 68,
    },
  },
},
    reporters: ["progress", "kjhtml"],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    // Added for CI: headless Chrome with the flags GitHub-hosted runners need
    customLaunchers: {
      ChromeHeadlessCI: {
        base: "ChromeHeadless",
        flags: ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
      },
    },
    browsers: ["Chrome"],
    singleRun: false,
    restartOnFileChange: true,
  });
};
