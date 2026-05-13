FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY investment-fund-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY investment-fund-service/ .

ARG SERVER_PORT=8092
EXPOSE ${SERVER_PORT}

CMD ["sh", "-c", "alembic upgrade head && uvicorn main:create_app --factory --host ${HOST:-0.0.0.0} --port ${PORT:-8092}"]
