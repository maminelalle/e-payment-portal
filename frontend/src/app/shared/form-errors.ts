import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

import { ApiProblem } from '../core/api.models';

/**
 * Reporte les erreurs par champ renvoyées par l'API sur les contrôles du formulaire.
 * Renvoie le message général à afficher (ou null si tout a été rattaché à des champs).
 */
export function applyServerErrors(form: FormGroup, error: unknown): string | null {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Une erreur inattendue est survenue.';
  }
  const problem = error.error as ApiProblem | null;
  let unmatched = false;
  for (const [field, message] of Object.entries(problem?.errors ?? {})) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
    } else {
      unmatched = true;
    }
  }
  if (problem?.errors && !unmatched) {
    return null;
  }
  return problem?.detail ?? 'Une erreur est survenue. Réessayez.';
}

/** Message à afficher sous un champ : erreur serveur, sinon message local fourni. */
export function fieldError(form: FormGroup, field: string, messages: Record<string, string>): string | null {
  const control = form.get(field);
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  if (control.errors['server']) {
    return control.errors['server'];
  }
  const key = Object.keys(control.errors).find((k) => messages[k]);
  return key ? messages[key]! : null;
}
