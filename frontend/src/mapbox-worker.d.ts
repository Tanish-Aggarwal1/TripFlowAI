// Ambient type for Vite's `?worker` import suffix, used to load Mapbox's
// CSP worker as a properly bundled Worker constructor — see trip-map.component.ts.
declare module '*?worker' {
  const WorkerConstructor: { new (): Worker };
  export default WorkerConstructor;
}
