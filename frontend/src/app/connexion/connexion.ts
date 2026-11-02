import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-connexion',
  imports: [RouterLink, ButtonModule],
  templateUrl: './connexion.html',
  styleUrl: './connexion.scss',
})
export class Connexion {
  protected readonly atouts = [
    'Signature électronique du contrat',
    'Accès limité aux moyens prévus au contrat',
    'Historique et reçu pour chaque transaction',
  ];
}
