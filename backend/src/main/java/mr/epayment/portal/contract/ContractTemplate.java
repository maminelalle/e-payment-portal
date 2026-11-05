package mr.epayment.portal.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modèle de contrat versionné. Un modèle publié n'est jamais modifié : on crée une nouvelle version. */
@Entity
@Table(name = "contract_template")
class ContractTemplate {

    @Id
    private UUID id;

    private String code;

    private String name;

    private int version;

    @Column(name = "content_html")
    private String contentHtml;

    private String status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ContractTemplate() {
    }

    UUID getId() {
        return id;
    }

    String getContentHtml() {
        return contentHtml;
    }

    boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** Seul le statut change : le contenu d'une version publiée est figé (D14). */
    void changeStatus(boolean active) {
        this.status = active ? "ACTIVE" : "ARCHIVED";
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    int getVersion() {
        return version;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    TemplateView toView() {
        return new TemplateView(id, code, name, version);
    }

    public record TemplateView(UUID id, String code, String name, int version) {
    }
}
