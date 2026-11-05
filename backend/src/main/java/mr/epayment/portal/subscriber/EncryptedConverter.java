package mr.epayment.portal.subscriber;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Colonne chiffrée (NNI, NIF) : en clair dans l'application, chiffrée en base. */
@Converter
class EncryptedConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plain) {
        return SensitiveData.encrypt(plain);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return SensitiveData.decrypt(stored);
    }
}
