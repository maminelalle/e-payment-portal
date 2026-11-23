-- Pied de page du contrat : les zones de marge (@page) n'héritent pas de la police du body et s'affichaient en
-- police à empattements. Même police que le document. Les PDF déjà envoyés sont figés (D15).
UPDATE contract_template
SET content_html = replace(replace(content_html,
    '@bottom-left { content: "Portail E-Payment"; font-size: 8pt; color: #64748b; }',
    '@bottom-left { content: "Portail E-Payment"; font-family: sans-serif; font-size: 8pt; color: #64748b; }'),
    '@bottom-right { content: "Page " counter(page) " / " counter(pages); font-size: 8pt; color: #64748b; }',
    '@bottom-right { content: "Page " counter(page) " / " counter(pages); font-family: sans-serif; font-size: 8pt; color: #64748b; }')
WHERE code = 'STANDARD';
