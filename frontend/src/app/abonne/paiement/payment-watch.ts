import { exhaustMap, Observable, take, takeWhile, timer } from 'rxjs';

import { isFinal, Transaction } from '../../core/api.models';
import { AbonneApi } from '../abonne-api';

/**
 * Suit un paiement en attente jusqu'à son issue : toutes les 2 s, le serveur redemande le statut
 * au fournisseur (le webhook peut aussi l'avoir déjà mis à jour). Abandon après 90 s : le paiement
 * reste visible dans l'historique et se mettra à jour seul.
 */
export function watchPayment(api: AbonneApi, reference: string): Observable<Transaction> {
  return timer(0, 2000).pipe(
    take(45),
    exhaustMap(() => api.payment(reference, true)),
    takeWhile((tx) => !isFinal(tx.status), true),
  );
}
