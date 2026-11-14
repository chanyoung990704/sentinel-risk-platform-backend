ARG PYTHON_IMAGE
FROM ${PYTHON_IMAGE}
WORKDIR /app
COPY server.py /app/server.py
USER 65534:65534
EXPOSE 8080
ENTRYPOINT ["python", "-u", "/app/server.py"]
