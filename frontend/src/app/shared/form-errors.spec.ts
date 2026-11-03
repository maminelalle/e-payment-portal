import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { applyServerErrors, fieldError } from './form-errors';

describe('applyServerErrors', () => {
  function form() {
    return new FormGroup({ nni: new FormControl(''), phone: new FormControl('') });
  }

  function problem(status: number, body: object) {
    return new HttpErrorResponse({ status, error: body });
  }

  it('rattache chaque erreur au champ du formulaire', () => {
    const f = form();
    const message = applyServerErrors(
      f,
      problem(400, { detail: 'Certains champs sont invalides.', errors: { nni: 'Le NNI est obligatoire.' } }),
    );
    expect(message).toBeNull();
    expect(fieldError(f, 'nni', {})).toBe('Le NNI est obligatoire.');
  });

  it("renvoie le message général d'un conflit", () => {
    expect(applyServerErrors(form(), problem(409, { detail: 'Un abonné utilise déjà cet email.' }))).toBe(
      'Un abonné utilise déjà cet email.',
    );
  });

  it("renvoie le message général si une erreur ne correspond à aucun champ", () => {
    const message = applyServerErrors(
      form(),
      problem(400, { detail: 'Certains champs sont invalides.', errors: { inconnu: 'x' } }),
    );
    expect(message).toBe('Certains champs sont invalides.');
  });
});

describe('fieldError', () => {
  it("n'affiche rien tant que le champ n'a pas été touché", () => {
    const f = new FormGroup({ email: new FormControl('', Validators.required) });
    expect(fieldError(f, 'email', { required: 'Obligatoire' })).toBeNull();
    f.controls.email.markAsTouched();
    expect(fieldError(f, 'email', { required: 'Obligatoire' })).toBe('Obligatoire');
  });
});
