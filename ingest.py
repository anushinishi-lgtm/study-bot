import os
import fitz  # PyMuPDF
from dotenv import load_dotenv
from langchain.text_splitter import CharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain.docstore.document import Document
from langchain_google_genai import GoogleGenerativeAIEmbeddings # CHANGED

# --- CONFIGURATION ---
DATA_DIR = "docs"
VECTORSTORE_DIR = "vectorstore"
CHUNK_SIZE = 1000
CHUNK_OVERLAP = 200

def extract_text_from_pdf(pdf_path):
    """Extracts text from a single PDF file."""
    try:
        doc = fitz.open(pdf_path)
        full_text = ""
        for page in doc:
            full_text += page.get_text("text") + "\n"
        return full_text
    except Exception as e:
        print(f"Error reading {pdf_path}: {e}")
        return None

def main():
    """
    Main function to load documents, split them into chunks,
    create embeddings using Google's model, and save them to a FAISS vectorstore.
    """
    # NEW: Load environment variables from .env file
    load_dotenv()
    if not os.getenv("GOOGLE_API_KEY"):
        print("🔴 GOOGLE_API_KEY not found in .env file. Please add it to continue.")
        return

    print("Starting document ingestion process...")

    # --- 1. Load Documents ---
    docs = []
    for file in os.listdir(DATA_DIR):
        if file.lower().endswith(".pdf"):
            pdf_path = os.path.join(DATA_DIR, file)
            text = extract_text_from_pdf(pdf_path)
            if text:
                docs.append(Document(page_content=text, metadata={"source": file}))
                print(f"✅ Loaded {file}")

    if not docs:
        print("No PDF documents found in the 'docs' directory. Exiting.")
        return

    # --- 2. Split Documents into Chunks ---
    splitter = CharacterTextSplitter(chunk_size=CHUNK_SIZE, chunk_overlap=CHUNK_OVERLAP)
    chunks = splitter.split_documents(docs)
    print(f"Split documents into {len(chunks)} chunks.")

    # --- 3. Create Embeddings and FAISS Vectorstore ---
    print("Initializing Google's embedding model...")
    # CHANGED: Use GoogleGenerativeAIEmbeddings instead of Ollama
    embeddings = GoogleGenerativeAIEmbeddings(model="models/text-embedding-004")

    print("Creating FAISS vectorstore. This may take a while for large documents...")
    # OPTIMIZED: Create the vectorstore from all chunks at once for efficiency
    db = FAISS.from_documents(chunks, embeddings)

    # --- 4. Save the Vectorstore ---
    os.makedirs(VECTORSTORE_DIR, exist_ok=True)
    db.save_local(VECTORSTORE_DIR)
    print(f"✅ Vector database saved successfully in '{VECTORSTORE_DIR}'!")

if __name__ == "__main__":
    main()