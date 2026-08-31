from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.api.dependencies import services
from app.domain.russian_syllabifier import syllabify
from app.services.container import ServiceContainer

router = APIRouter(prefix="/api/examples", tags=["examples"])


class PreprocessRequest(BaseModel):
    text: str = Field(min_length=1, max_length=500)


@router.post("/preprocess")
def preprocess(request: PreprocessRequest, container: ServiceContainer = Depends(services)):
    words, warnings = container.stress.words(request.text)
    stressed_text, _ = container.stress.accent_text(request.text)
    return {
        "text": request.text,
        "stressed_text": stressed_text,
        "words": [
            {
                "text": word.text,
                "stressed_text": word.stressed_text,
                "stress_syllable_index": word.stress_syllable_index,
                "syllables": [
                    {
                        "text": item.text,
                        "index": item.index,
                        "vowel": item.vowel,
                        "is_stressed": item.is_stressed,
                    }
                    for item in syllabify(word.stressed_text)
                ],
            }
            for word in words
        ],
        "warnings": warnings,
    }
