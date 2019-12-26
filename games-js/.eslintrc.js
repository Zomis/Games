module.exports = {
  root: true,
  env: {
    node: true
  },
  'extends': [
    'plugin:vue/essential',
    'eslint:recommended'
  ],
  rules: {
    'no-func-assign': 'warn',
    'no-constant-condition': 'warn',
    'no-unused-vars': 'off',
    'no-unused-labels': 'warn',
    'no-self-assign': 'warn',
    'no-undef': 'off'
  },
  parserOptions: {
    parser: 'babel-eslint'
  },
  overrides: [
    {
      files: [
        '**/__tests__/*.{j,t}s?(x)'
      ],
      env: {
        mocha: true
      }
    }
  ]
}
