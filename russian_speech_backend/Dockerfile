FROM continuumio/miniconda3:24.11.1-0

WORKDIR /srv/app
RUN conda install -y -c conda-forge python=3.11 ffmpeg montreal-forced-aligner \
    && conda clean -afy

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY scripts ./scripts
RUN mkdir -p /srv/app/temp /srv/app/models

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
