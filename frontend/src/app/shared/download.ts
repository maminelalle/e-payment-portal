/** Enregistre un fichier reçu de l'API (reçu PDF, export CSV) sous le nom indiqué. */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  // Lien attaché au document le temps du clic : nécessaire pour certains navigateurs (Firefox, Safari).
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
