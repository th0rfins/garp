FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .
COPY frontend/ frontend/

ENV GARP_PORT=8000
EXPOSE 8000
VOLUME ["/app/data"]

ENV GARP_DB=/app/data/garp.db
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000"]
