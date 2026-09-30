declare const __APP_VERSION__: string;

/** Version of this build: "X.Y.Z" for releases, "edge-<sha>" for main builds, "dev" locally. */
export const APP_VERSION: string = __APP_VERSION__;

/** Version as shown to users: releases get a "v" prefix, anything else is shown as is. */
export function formatVersion(version: string): string {
  return /^\d+\.\d+\.\d+/.test(version) ? `v${version}` : version;
}
