// @ts-check
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';

export default tseslint.config(
  {
    ignores: ['projects/**/*', 'src/environments/environment.local.ts'],
  },
  {
    files: ['**/*.ts'],
    extends: [...angular.configs.tsRecommended],
    languageOptions: {
      parserOptions: {
        project: ['tsconfig.app.json', 'tsconfig.spec.json'],
        createDefaultProgram: true,
      },
    },
    rules: {
      // Angular 22's own `ng update` migration set ChangeDetectionStrategy.Eager
      // on every pre-existing component to preserve pre-v22 default CD behavior,
      // rather than risk switching them to real OnPush sight-unseen. Enforcing
      // this rule would fight that migration's own choice; converting each
      // component to genuine OnPush is real, separate work (needs auditing
      // each one's mutation patterns), not a version-bump task.
      '@angular-eslint/prefer-on-push-component-change-detection': 'off',
      '@angular-eslint/component-class-suffix': ['error', { suffixes: ['Page', 'Component'] }],
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'app', style: 'kebab-case' }],
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'app', style: 'camelCase' }],
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended],
    rules: {},
  },
);
