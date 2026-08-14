import axe from 'axe-core';

// L-13 (.refactor/frontend.md): specs assert against the component class, never the
// rendered DOM, so missing accessible names (M-7) were invisible to a green suite.
//
// Disables two rules on purpose, rather than allow-listing: color-contrast and the
// decorative-icon role-img-alt finding are pre-existing, app-wide, and tracked as
// their own separate audit items. Everything else stays on.
const DISABLED_RULES: Record<string, { enabled: boolean }> = {
  'color-contrast': { enabled: false },
  'role-img-alt': { enabled: false },
};

// Ionic's Stencil components hydrate asynchronously outside NgZone (their own
// requestAnimationFrame-based scheduler, not zone-patched promises/timers), so
// fixture.detectChanges() alone leaves ion-input/ion-select/etc. as unhydrated
// custom elements with no rendered native control for axe to inspect — a double
// rAF is the standard wait for Stencil's first render to land.
async function waitForHydration(): Promise<void> {
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
}

export async function expectNoA11yViolations(root: Element): Promise<void> {
  await waitForHydration();
  const results = await axe.run(root, { rules: DISABLED_RULES });
  if (results.violations.length > 0) {
    const details = results.violations
      .map(
        (violation) =>
          `${violation.id} (${violation.impact}): ${violation.description}\n` +
          violation.nodes.map((node) => `  - ${node.target.join(' ')}`).join('\n'),
      )
      .join('\n\n');
    fail(`Accessibility violations found:\n\n${details}`);
  }
}

// axe's "label" rule doesn't catch this specific pattern: Ionic always renders a
// wrapping <label for="..."> around the native control regardless of whether the
// `label` prop was set, so an accessible-name source structurally exists even when
// it's empty — the exact M-7 bug (SCRUM-309) that shipped with a fully green,
// axe-clean suite. This checks for an actual text source instead: either the
// Ionic-level `label` attribute (the position="stacked" pattern M-7 fixed), or a
// sibling <ion-label> with text inside the same ion-item (the other valid Ionic
// pattern, e.g. trip-edit.page.html's Visibility select).
export function expectAllFormControlsLabeled(root: Element): void {
  const controls = root.querySelectorAll('ion-input, ion-select, ion-textarea');
  controls.forEach((control) => {
    const label = control.getAttribute('label')?.trim();
    // ion-label overrides textContent (returns '' even when it has visible text), so
    // innerText is used here instead — it reflects what's actually rendered.
    const labelEl = control.parentElement?.querySelector('ion-label') as HTMLElement | null;
    const siblingLabel = labelEl?.innerText?.trim();
    if (!label && !siblingLabel) {
      fail(`<${control.tagName.toLowerCase()}> has no accessible label: ${control.outerHTML}`);
    }
  });
}
