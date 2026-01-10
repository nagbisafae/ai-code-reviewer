"""
FastAPI service for Java vulnerability detection
Trained ML Model: GraphCodeBERT (83.93% accuracy)
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch
from typing import Optional
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize FastAPI
app = FastAPI(
    title="Java Vulnerability Detector API",
    description="ML-powered vulnerability detection using trained GraphCodeBERT model",
    version="1.0.0"
)

# Add CORS middleware (allows Spring Boot to call this API)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify your Spring Boot URL
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global variables for model and tokenizer
MODEL_PATH = "./model/java-vulnerability-detector-final"
tokenizer = None
model = None
device = None


@app.on_event("startup")
async def load_model():
    """
    Load model and tokenizer at startup (once)
    This ensures fast predictions later
    """
    global tokenizer, model, device

    try:
        logger.info("=" * 60)
        logger.info("LOADING ML MODEL...")
        logger.info("=" * 60)

        # Detect device (GPU if available, otherwise CPU)
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"Using device: {device}")

        # Load tokenizer
        # Load tokenizer from HuggingFace Hub (more reliable)
        logger.info("Loading tokenizer from HuggingFace Hub...")
        tokenizer = AutoTokenizer.from_pretrained("microsoft/graphcodebert-base")
        logger.info("Tokenizer loaded")

        # Load model
        logger.info("Loading model...")
        model = AutoModelForSequenceClassification.from_pretrained(MODEL_PATH)
        model.to(device)
        model.eval()  # Set to evaluation mode
        logger.info("Model loaded and ready!")

        logger.info("=" * 60)
        logger.info("ML MODEL SERVICE READY!")
        logger.info(f"Model Accuracy: 83.93%")
        logger.info(f"Task: Java Vulnerability Detection")
        logger.info("=" * 60)

    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        raise RuntimeError(f"Model loading failed: {e}")


# Request/Response Models
class CodeAnalysisRequest(BaseModel):
    """Request body for code analysis"""
    code: str
    filename: str = "unknown.java"

    class Config:
        json_schema_extra = {
            "example": {
                "code": 'String query = "SELECT * FROM users WHERE id = " + userId;',
                "filename": "UserDAO.java"
            }
        }


class CodeAnalysisResponse(BaseModel):
    """Response body with vulnerability prediction"""
    is_vulnerable: bool
    confidence: float
    prediction: str
    filename: str

    class Config:
        json_schema_extra = {
            "example": {
                "is_vulnerable": True,
                "confidence": 0.9234,
                "prediction": "VULNERABLE",
                "filename": "UserDAO.java"
            }
        }


@app.get("/")
def root():
    """
    Root endpoint - Basic service info
    """
    return {
        "service": "Java Vulnerability Detector",
        "status": "running",
        "model": "GraphCodeBERT",
        "accuracy": "83.93%",
        "version": "1.0.0",
        "endpoints": {
            "analyze": "POST /analyze",
            "health": "GET /health",
            "docs": "GET /docs"
        }
    }


@app.get("/health")
def health_check():
    """
    Detailed health check endpoint
    """
    return {
        "status": "healthy",
        "model_loaded": model is not None,
        "tokenizer_loaded": tokenizer is not None,
        "device": str(device),
        "ready": model is not None and tokenizer is not None
    }


@app.post("/analyze", response_model=CodeAnalysisResponse)
def analyze_code(request: CodeAnalysisRequest) -> CodeAnalysisResponse:
    """
    Analyze Java code for security vulnerabilities

    **Trained Model:** GraphCodeBERT fine-tuned on 3,591 Java examples

    **Accuracy:** 83.93%

    **Returns:**
    - is_vulnerable: Boolean indicating if code is vulnerable
    - confidence: Model's confidence score (0-1)
    - prediction: "VULNERABLE" or "SAFE"
    - filename: Name of the analyzed file

    **Detection capabilities:**
    - SQL Injection
    - Cross-Site Scripting (XSS)
    - Path Traversal
    - Command Injection
    - Insecure Deserialization
    - And more...
    """

    # Validate model is loaded
    if model is None or tokenizer is None:
        raise HTTPException(
            status_code=503,
            detail="Model not loaded. Please wait for startup to complete."
        )

    try:
        logger.info(f"Analyzing: {request.filename}")
        logger.info(f"Code length: {len(request.code)} characters")

        # Tokenize input
        inputs = tokenizer(
            request.code,
            return_tensors="pt",
            padding=True,
            truncation=True,
            max_length=512
        )

        # Move to same device as model
        inputs = {k: v.to(device) for k, v in inputs.items()}

        # Get prediction
        with torch.no_grad():
            outputs = model(**inputs)
            logits = outputs.logits
            probabilities = torch.softmax(logits, dim=-1)
            prediction_idx = torch.argmax(logits, dim=-1).item()
            confidence = probabilities[0][prediction_idx].item()

        # Interpret results
        # Label 0 = safe, Label 1 = vulnerable (based on your training)
        is_vulnerable = (prediction_idx == 1)
        prediction_label = "VULNERABLE" if is_vulnerable else "SAFE"

        logger.info(f"✅ Prediction: {prediction_label} (confidence: {confidence:.2%})")

        return CodeAnalysisResponse(
            is_vulnerable=is_vulnerable,
            confidence=round(confidence, 4),
            prediction=prediction_label,
            filename=request.filename
        )

    except Exception as e:
        logger.error(f"Analysis failed: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Analysis failed: {str(e)}"
        )


@app.get("/stats")
def get_stats():
    """
    Get model statistics and information
    """
    return {
        "model_name": "GraphCodeBERT",
        "architecture": "microsoft/graphcodebert-base",
        "task": "Binary Classification (Safe/Vulnerable)",
        "training_data": {
            "dataset": "mcanoglu/defect-detection (Java only) + augmentation",
            "examples": 3591,
            "augmentation": "Variable renaming, comments, whitespace"
        },
        "performance": {
            "test_accuracy": "83.93%",
            "precision": "84.00%",
            "recall": "83.44%",
            "f1_score": "83.72%"
        },
        "capabilities": [
            "SQL Injection detection",
            "Cross-Site Scripting (XSS)",
            "Path Traversal",
            "Command Injection",
            "Insecure Deserialization",
            "XXE (XML External Entity)",
            "Hardcoded secrets",
            "And more security vulnerabilities"
        ]
    }


if __name__ == "__main__":
    import uvicorn

    print("=" * 60)
    print("🚀 Starting Java Vulnerability Detector API")
    print("=" * 60)

    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8000,
        log_level="info"
    )