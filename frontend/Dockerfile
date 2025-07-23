FROM python:3.11-slim

WORKDIR /app

# Install ca-certificates to fix SSL issues
RUN apt-get update && apt-get install -y ca-certificates curl && update-ca-certificates && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --trusted-host pypi.org --trusted-host files.pythonhosted.org --trusted-host pypi.python.org --timeout 300 -r requirements.txt

COPY . .

EXPOSE 8501

HEALTHCHECK CMD curl --fail http://localhost:8501/_stcore/health

ENTRYPOINT ["streamlit", "run", "app.py", "--server.port=8501", "--server.address=0.0.0.0"] 