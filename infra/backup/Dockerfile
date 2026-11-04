# Sauvegardes : client PostgreSQL 16 + AWS CLI (compatible SeaweedFS et tout stockage S3).
FROM alpine:3.22
RUN apk add --no-cache postgresql16-client aws-cli tzdata
ENV TZ=Africa/Nouakchott
COPY backup.sh restore.sh run-daily.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/*.sh
ENTRYPOINT ["run-daily.sh"]
