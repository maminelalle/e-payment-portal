import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
})
export class AdminLayout {
  protected readonly menu = [
    { libelle: 'Tableau de bord', icone: 'pi pi-th-large', lien: '/admin', exact: true },
    { libelle: 'Abonnés', icone: 'pi pi-users', lien: '/admin/abonnes', exact: false },
    { libelle: 'Moyens de paiement', icone: 'pi pi-credit-card', lien: '/admin/moyens', exact: false },
    { libelle: 'Contrats', icone: 'pi pi-file', lien: '/admin/contrats', exact: false },
    { libelle: 'Transactions', icone: 'pi pi-arrow-right-arrow-left', lien: '/admin/transactions', exact: false },
    { libelle: 'Paramètres et audit', icone: 'pi pi-shield', lien: '/admin/audit', exact: false },
  ];
}
