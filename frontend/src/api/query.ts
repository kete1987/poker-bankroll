/** Values a query parameter can take; a list is sent as the parameter repeated per value. */
type QueryValue = string | number | boolean | readonly (string | number)[] | undefined;

/** Query string of a request: undefined, empty text and empty lists are left out. */
export function toQueryString(query: object): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query) as [string, QueryValue][]) {
    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, String(item)));
    } else if (value !== undefined && value !== '') {
      params.set(key, String(value));
    }
  }
  return params.toString();
}
