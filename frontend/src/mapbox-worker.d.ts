// Ambient type for Vite's `?worker` import suffix, used to load Mapbox's
// CSP worker as a properly bundled Worker constructor — see trip-map.component.ts.
// This depends on builder-level support for the `?worker` suffix (present in
// Angular's dev-server, which runs on Vite). A `ng build` succeeding is not
// proof the worker actually loads at runtime — TypeScript is satisfied by this
// ambient declaration regardless of whether the resolution behind it still works.
declare module '*?worker' {
  const WorkerConstructor: { new (): Worker };
  export default WorkerConstructor;
}
