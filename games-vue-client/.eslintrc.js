module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    "plugin:vue/essential",
    "plugin:vue/recommended",
    'eslint:recommended',
  ],
  "ignorePatterns": ["games-js.js"],
  rules: {
    "vue/no-use-v-if-with-v-for": "warn",
    "vue/require-prop-types": "off",
    "vue/order-in-components": "off",
    "vue/no-v-html": "off",
    "vue/require-default-prop": "off",
    "no-console": process.env.NODE_ENV === "production" ? "warn" : "off",
    "no-debugger": process.env.NODE_ENV === "production" ? "error" : "warn",
  },
  parserOptions: {
    parser: "babel-eslint",
  },
  overrides: [
    {
      files: [
        "**/__tests__/*.{j,t}s?(x)",
        "**/tests/unit/**/*.spec.{j,t}s?(x)",
      ],
      env: {
        mocha: true,
      },
    },
  ],
};
