/** Hands a file made in the browser, or downloaded by it, to the user: the browser saves it. */
export function saveFile(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  // Some browsers only follow a link that is in the document.
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
