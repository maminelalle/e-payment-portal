import { DOCUMENT } from '@angular/common';
import { effect, inject, Injectable } from '@angular/core';

import { Preferences } from './preferences';

type TranslationMap = Record<string, string>;

const ARABIC: TranslationMap = {
  'Accueil': 'الرئيسية',
  'Mon contrat': 'عقدي',
  'Moyens de paiement': 'وسائل الدفع',
  'Transactions': 'المعاملات',
  'Profil': 'الملف الشخصي',
  'Tableau de bord': 'لوحة التحكم',
  'Abonnés': 'المشتركون',
  'Contrats': 'العقود',
  'Paramètres et audit': 'الإعدادات والتدقيق',
  'Aucune notification.': 'لا توجد إشعارات.',
  'Préférences d’affichage': 'تفضيلات العرض',
  'Préférences d\'affichage': 'تفضيلات العرض',
  'Mode clair': 'الوضع الفاتح',
  'Mode sombre': 'الوضع الداكن',
  'Notifications': 'الإشعارات',
  'Se déconnecter': 'تسجيل الخروج',
  'Mon profil': 'ملفي الشخصي',
  'Connexion': 'تسجيل الدخول',
  'Se connecter': 'دخول',
  'Accédez à votre espace E-Payment': 'الوصول إلى مساحة E-Payment الخاصة بك',
  'Vos paiements, encadrés par votre contrat.': 'مدفوعاتك مؤطرة بعقدك.',
  'Un seul portail pour gérer les contrats, signer en ligne et payer avec les moyens autorisés.':
    'بوابة واحدة لإدارة العقود والتوقيع إلكترونياً والدفع بوسائل الدفع المصرح بها.',
  'Signature électronique du contrat': 'التوقيع الإلكتروني للعقد',
  'Accès limité aux moyens prévus au contrat': 'الوصول إلى وسائل الدفع المنصوص عليها في العقد',
  'Historique et reçu pour chaque transaction': 'سجل وإيصال لكل معاملة',
  'Administrateur ou abonné : l’espace s’adapte à votre compte.':
    'مسؤول أو مشترك: تتكيف المساحة مع حسابك.',
  'Administrateur ou abonné : l\'espace s\'adapte à votre compte.':
    'مسؤول أو مشترك: تتكيف المساحة مع حسابك.',
  'Catalogue des wallets et cartes proposés dans les contrats': 'دليل المحافظ والبطاقات المتاحة في العقود',
  'Wallet mobile': 'محفظة هاتفية',
  'Carte bancaire': 'بطاقة مصرفية',
  'Statut': 'الحالة',
  'Fournisseur': 'المزود',
  'Disponibilité': 'التوفر',
  'Devise': 'العملة',
  'Actif': 'نشط',
  'Inactif': 'غير نشط',
  'Disponible': 'متاح',
  'Indisponible': 'غير متاح',
  'Non vérifiée': 'غير متحقق منها',
  'Changer le logo': 'تغيير الشعار',
  'Ajouter un logo': 'إضافة شعار',
  'Retirer': 'إزالة',
  'Chargement…': 'جار التحميل…',
  'Nouvel abonné': 'مشترك جديد',
  'Nom, référence, email ou téléphone': 'الاسم أو المرجع أو البريد الإلكتروني أو الهاتف',
  'Tous les statuts': 'كل الحالات',
  'Tous les types': 'كل الأنواع',
  'Référence': 'المرجع',
  'Nom / raison sociale': 'الاسم / الاسم التجاري',
  'Téléphone': 'الهاتف',
  'Aucun abonné ne correspond à ces critères.': 'لا يوجد مشترك يطابق هذه المعايير.',
  'Type d’abonné': 'نوع المشترك',
  'Type d\'abonné': 'نوع المشترك',
  'Raison sociale': 'الاسم التجاري',
  'Nom complet': 'الاسم الكامل',
  'Numéro d’identification fiscale de l’entreprise': 'رقم التعريف الضريبي للشركة',
  'Numéro d\'identification fiscale de l\'entreprise': 'رقم التعريف الضريبي للشركة',
  'Numéro national d’identification (10 chiffres)': 'رقم التعريف الوطني (10 أرقام)',
  'Numéro national d\'identification (10 chiffres)': 'رقم التعريف الوطني (10 أرقام)',
  'Adresse': 'العنوان',
  'Créer l’abonné': 'إنشاء المشترك',
  'Créer l\'abonné': 'إنشاء المشترك',
  'Vue d’ensemble de l’activité de la plateforme': 'نظرة عامة على نشاط المنصة',
  'Vue d\'ensemble de l\'activité de la plateforme': 'نظرة عامة على نشاط المنصة',
  'Abonnés actifs': 'المشتركون النشطون',
  'Contrats en attente de signature': 'العقود في انتظار التوقيع',
  'Transactions ce mois': 'معاملات هذا الشهر',
  'Montant encaissé ce mois': 'المبلغ المحصل هذا الشهر',
  'Voir les contrats': 'عرض العقود',
  'Volume des transactions': 'حجم المعاملات',
  'Nombre par jour': 'العدد يومياً',
  'Répartition par moyen': 'التوزيع حسب وسيلة الدفع',
  'Aucune transaction sur la période.': 'لا توجد معاملات خلال هذه الفترة.',
  'Contrats à traiter': 'العقود المطلوب معالجتها',
  'Voir tout': 'عرض الكل',
  'Aucun contrat en attente d’action.': 'لا توجد عقود تنتظر إجراءً.',
  'Aucun contrat en attente d\'action.': 'لا توجد عقود تنتظر إجراءً.',
  'Dernières transactions': 'آخر المعاملات',
  'Aucune transaction pour l’instant.': 'لا توجد معاملات حالياً.',
  'Aucune transaction pour l\'instant.': 'لا توجد معاملات حالياً.',
  'Bonjour,': 'مرحباً،',
  'Voici la situation de votre compte au': 'إليك حالة حسابك بتاريخ',
  'Votre contrat est actif': 'عقدك نشط',
  'Votre contrat est prêt à être signé': 'عقدك جاهز للتوقيع',
  'Contrat signé, en attente d’activation': 'تم توقيع العقد، في انتظار التفعيل',
  'Contrat signé, en attente d\'activation': 'تم توقيع العقد، في انتظار التفعيل',
  'Votre contrat est suspendu': 'عقدك موقوف',
  'Aucun contrat pour l’instant': 'لا يوجد عقد حالياً',
  'Aucun contrat pour l\'instant': 'لا يوجد عقد حالياً',
  'Votre contrat est terminé': 'انتهى عقدك',
  'Signer mon contrat': 'توقيع عقدي',
  'Voir le contrat': 'عرض العقد',
  'Progression du contrat': 'مراحل العقد',
  'Brouillon': 'مسودة',
  'À signer': 'بانتظار التوقيع',
  'Signé': 'موقّع',
  'Ce mois-ci': 'هذا الشهر',
  'Montant payé': 'المبلغ المدفوع',
  'Paiements réussis': 'المدفوعات الناجحة',
  'Paiements échoués': 'المدفوعات الفاشلة',
  'Plafonds mensuels': 'الحدود الشهرية',
  'Mes moyens de paiement': 'وسائل الدفع الخاصة بي',
  'Derniers emails reçus': 'آخر رسائل البريد المستلمة',
  'Coordonnées': 'البيانات الشخصية',
  'Raison sociale / nom': 'الاسم التجاري / الاسم',
  'Référence abonné': 'مرجع المشترك',
  'Email de connexion': 'بريد تسجيل الدخول',
  'Mot de passe': 'كلمة المرور',
  'Changer mon mot de passe': 'تغيير كلمة المرور',
  'Sessions ouvertes': 'الجلسات المفتوحة',
  'Me déconnecter partout': 'تسجيل الخروج من جميع الأجهزة',
  'Aucune session ouverte.': 'لا توجد جلسات مفتوحة.',
  'Cet appareil': 'هذا الجهاز',
  'Fermer': 'إغلاق',
  'Envoyé': 'تم الإرسال',
  'Échec d’envoi': 'فشل الإرسال',
  'Échec d\'envoi': 'فشل الإرسال',
  'En cours': 'قيد التنفيذ',
  'Aucun email pour l’instant.': 'لا توجد رسائل حالياً.',
  'Aucun email pour l\'instant.': 'لا توجد رسائل حالياً.',
  'Payer': 'دفع',
  'Montant': 'المبلغ',
  'Motif (facultatif)': 'السبب (اختياري)',
  'Télécharger le reçu': 'تحميل الإيصال',
  'Retour à l’accueil': 'العودة إلى الرئيسية',
  'Retour à l\'accueil': 'العودة إلى الرئيسية',
  'Continuer vers le paiement': 'متابعة إلى الدفع',
  'Redirection vers la page sécurisée de la passerelle bancaire…': 'إعادة التوجيه إلى صفحة بوابة الدفع المصرفية الآمنة…',
  'Vous y saisirez votre carte ; le portail ne voit jamais son numéro.': 'ستدخل بطاقتك هناك؛ البوابة لا ترى رقمها أبداً.',
  'Annuler': 'إلغاء',
  'à': 'في',
  // En-tête, notifications, recherche
  'Langue': 'اللغة',
  'Ouvrir le menu': 'فتح القائمة',
  'Navigation': 'التنقل',
  'Rechercher (Ctrl+K)': 'بحث (Ctrl+K)',
  'Rechercher un abonné, un contrat, une transaction…': 'ابحث عن مشترك أو عقد أو معاملة…',
  'Saisissez au moins 2 caractères.': 'أدخل حرفين على الأقل.',
  'Pages': 'الصفحات',
  'Journal d’audit': 'سجل التدقيق',
  'Nouveau contrat': 'عقد جديد',
  'Fournisseurs': 'المزودون',
  'Vérification…': 'جار التحقق…',
  'En ligne': 'متصل',
  'En panne': 'معطل',
  'Simulé': 'محاكى',
  'Opérationnel': 'يعمل',
  // Tableaux et statuts
  'Date': 'التاريخ',
  'Abonné': 'المشترك',
  'Moyen': 'الوسيلة',
  'Reçu': 'الإيصال',
  'Action': 'الإجراء',
  'Acteur': 'المنفذ',
  'Ressource': 'المورد',
  'Résultat': 'النتيجة',
  'Adresse IP': 'عنوان IP',
  'Système': 'النظام',
  'Réussite': 'نجاح',
  'Échec': 'فشل',
  'Créée': 'منشأة',
  'En attente': 'قيد الانتظار',
  'Réussie': 'ناجحة',
  'Échouée': 'فاشلة',
  'Annulée': 'ملغاة',
  'Remboursée': 'مستردة',
  'En attente de signature': 'في انتظار التوقيع',
  'Signé · à activer': 'موقّع · بانتظار التفعيل',
  'Suspendu': 'موقوف',
  'Expiré': 'منتهي',
  'Résilié': 'مفسوخ',
  'Désactivé': 'معطّل',
  'Personne physique': 'شخص طبيعي',
  'Personne morale': 'شخص معنوي',
  'Aucun paiement pour l’instant.': 'لا توجد مدفوعات حالياً.',
  'Aucun paiement pour l\'instant.': 'لا توجد مدفوعات حالياً.',
  'Aucun paiement ne correspond à ces critères.': 'لا توجد مدفوعات تطابق هذه المعايير.',
  'Aucune transaction ne correspond à ces critères.': 'لا توجد معاملات تطابق هذه المعايير.',
  'Aucun événement ne correspond à ces critères.': 'لا توجد أحداث تطابق هذه المعايير.',
  'Afficher plus': 'عرض المزيد',
  'Exporter en CSV': 'تصدير CSV',
  'Copier': 'نسخ',
  'Référence ou e-mail': 'المرجع أو البريد الإلكتروني',
  'Acteur (email)': 'المنفذ (البريد الإلكتروني)',
  'Toutes les actions': 'كل الإجراءات',
  'Tous les résultats': 'كل النتائج',
  'Période': 'الفترة',
  // Accueil abonné
  'Aucun moyen de paiement ouvert': 'لا توجد وسيلة دفع مفعّلة',
  'Ils apparaissent ici dès que votre contrat est signé et actif.': 'تظهر هنا بمجرد توقيع عقدك وتفعيله.',
  'Votre contrat expire dans 30 jours ou moins. Contactez votre gestionnaire pour le renouveler.':
    'ينتهي عقدك خلال 30 يوماً أو أقل. اتصل بمسؤول حسابك لتجديده.',
  'Email à chaque paiement': 'بريد إلكتروني عند كل عملية دفع',
  // Moyens de paiement admin
  'Tester les connexions': 'اختبار الاتصالات',
  'Retirer le logo': 'إزالة الشعار',
  'PNG, JPEG ou WebP · 256 Ko max · format carré conseillé': 'PNG أو JPEG أو WebP · 256 كيلوبايت كحد أقصى · يفضل شكل مربع',
  // Pages publiques
  'État du service': 'حالة الخدمة',
  'Portail E-Payment': 'بوابة E-Payment',
  'Tous les services fonctionnent normalement.': 'جميع الخدمات تعمل بشكل طبيعي.',
  'Un ou plusieurs moyens de paiement sont perturbés.': 'وسيلة دفع واحدة أو أكثر تواجه اضطراباً.',
  'Vérification d’un reçu': 'التحقق من إيصال',
  'Reçu authentique.': 'إيصال أصلي.',
  'Reçu non reconnu.': 'إيصال غير معروف.',
  'Payeur': 'الدافع',
  'Cette page n’affiche jamais de numéro de carte ni de téléphone complet.':
    'لا تعرض هذه الصفحة أبداً رقم بطاقة أو هاتف كاملاً.',
  // Tableau de bord, abonnés, contrats (admin)
  'Abonnés actifs ce mois': 'المشتركون النشطون هذا الشهر',
  'Tous les moyens': 'كل الوسائل',
  'Nouveau': 'جديد',
  'Modifier': 'تعديل',
  'Enregistrer': 'حفظ',
  'Suivant': 'التالي',
  'Précédent': 'السابق',
  'Validité': 'الصلاحية',
  'Créé le': 'أنشئ في',
  'Moyens': 'الوسائل',
  'Contrat': 'العقد',
  'Type': 'النوع',
  'Email': 'البريد الإلكتروني',
  'Actions': 'الإجراءات',
};

@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly document = inject(DOCUMENT);
  private readonly preferences = inject(Preferences);
  private readonly originals = new WeakMap<Node, string>();
  private observer?: MutationObserver;

  constructor() {
    effect(() => {
      this.preferences.language();
      queueMicrotask(() => this.translateDocument());
    });
    this.observer = new MutationObserver(() => this.translateDocument());
    this.observer.observe(this.document.body, { childList: true, subtree: true, characterData: true });
  }

  private translateDocument(): void {
    const arabic = this.preferences.language() === 'ar';
    const walker = this.document.createTreeWalker(this.document.body, NodeFilter.SHOW_TEXT);
    const textNodes: Text[] = [];
    let node: Node | null;
    while ((node = walker.nextNode())) textNodes.push(node as Text);

    for (const text of textNodes) {
      if (!this.originals.has(text) || !this.originals.get(text)) this.originals.set(text, text.nodeValue ?? '');
      const original = this.originals.get(text) ?? '';
      const trimmed = original.trim();
      if (!trimmed || text.parentElement?.closest('script,style')) continue;
      const translated = arabic ? this.translateText(trimmed) : trimmed;
      const leading = original.slice(0, original.indexOf(trimmed));
      const trailing = original.slice(original.indexOf(trimmed) + trimmed.length);
      const next = leading + translated + trailing;
      if (text.nodeValue !== next) text.nodeValue = next;
    }

    for (const element of Array.from(this.document.body.querySelectorAll<HTMLElement>('*'))) {
      for (const attribute of ['aria-label', 'title', 'placeholder']) {
        const value = element.getAttribute(attribute);
        if (!value) continue;
        // Clé data-* valide : « aria-label » → i18nAriaLabel (un tiret suivi d'une minuscule est refusé).
        const key = `i18n${attribute.replace(/(^|-)([a-z])/g, (_, __, c: string) => c.toUpperCase())}`;
        const original = element.dataset[key] ?? value;
        element.dataset[key] = original;
        const next = arabic ? this.translateText(original) : original;
        if (value !== next) element.setAttribute(attribute, next);
      }
    }
  }

  private translateText(text: string): string {
    if (ARABIC[text]) return ARABIC[text];
    if (text.startsWith('Bonjour, ')) return `مرحباً، ${text.slice('Bonjour, '.length)}`;
    if (text.startsWith('Reste ')) return `المتبقي ${text.slice('Reste '.length)}`;
    if (text.startsWith('Plafond atteint')) return 'تم بلوغ الحد: سيتم رفض المدفوعات الجديدة حتى الشهر المقبل';
    return text;
  }
}
