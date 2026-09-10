import streamlit as st
import json
import os
import fitz  # PyMuPDF
from langchain_google_genai import ChatGoogleGenerativeAI, GoogleGenerativeAIEmbeddings
from langchain_community.vectorstores import FAISS
from langchain.chains import ConversationalRetrievalChain
from langchain.prompts import PromptTemplate
from langchain.memory import ConversationBufferWindowMemory
from langchain.text_splitter import CharacterTextSplitter
from langchain.docstore.document import Document

# --- PAGE CONFIGURATION ---
st.set_page_config(page_title="Study Assistant", page_icon="🎓", layout="wide")

# --- CONSTANTS ---
PROFILE_FILE = "profile.json"
VECTORSTORE_DIR = "vectorstore"


def get_google_api_key():
    """Read the API key from Streamlit Cloud Secrets or a local environment."""
    return st.secrets.get("GOOGLE_API_KEY", os.getenv("GOOGLE_API_KEY"))

# --- HELPER FUNCTIONS (Profile & Progress) ---

def load_profile():
    """Loads profile and ensures 'tasks' list exists."""
    try:
        with open(PROFILE_FILE, "r") as f:
            data = json.load(f)
            if "tasks" not in data:
                data["tasks"] = [] # Ensure tasks list is present
            return data
    except (FileNotFoundError, json.JSONDecodeError):
        # Return a default structure if file doesn't exist or is empty
        return {"name": "Student", "notes": "No progress saved yet.", "tasks": []}

def save_profile(data):
    """Saves the user's profile to a JSON file."""
    with open(PROFILE_FILE, "w") as f:
        json.dump(data, f, indent=4)

# --- INGESTION LOGIC ---

def extract_text_from_pdf_bytes(pdf_bytes):
    """Extracts text from a PDF file provided as bytes."""
    try:
        with fitz.open(stream=pdf_bytes, filetype="pdf") as doc:
            full_text = "".join(page.get_text() for page in doc)
        return full_text
    except Exception as e:
        st.error(f"Error reading PDF content: {e}")
        return None

def process_and_store_documents(uploaded_files, embeddings):
    """Processes uploaded PDF files and adds them to the FAISS vector store."""
    all_chunks = []
    for file in uploaded_files:
        st.write(f"Processing `{file.name}`...")
        pdf_bytes = file.read()
        text = extract_text_from_pdf_bytes(pdf_bytes)
        if text:
            text_splitter = CharacterTextSplitter(chunk_size=1000, chunk_overlap=200)
            chunks = text_splitter.split_text(text)
            for i, chunk in enumerate(chunks):
                doc = Document(page_content=chunk, metadata={"source": file.name, "chunk": i})
                all_chunks.append(doc)

    if not all_chunks:
        st.warning("No text could be extracted from the documents. Aborting.")
        return

    if os.path.exists(VECTORSTORE_DIR):
        db = FAISS.load_local(VECTORSTORE_DIR, embeddings, allow_dangerous_deserialization=True)
        db.add_documents(all_chunks)
        st.info(f"Added {len(all_chunks)} new chunks to the knowledge base.")
    else:
        db = FAISS.from_documents(all_chunks, embeddings)
        st.info(f"Created a new knowledge base with {len(all_chunks)} chunks.")

    db.save_local(VECTORSTORE_DIR)
    st.success("✅ Knowledge base updated successfully!")

# --- CACHED COMPONENTS ---

@st.cache_resource
def load_components(api_key):
    """Loads and caches the heavy components (LLM, retriever)."""
    os.environ["GOOGLE_API_KEY"] = api_key
    embeddings = GoogleGenerativeAIEmbeddings(model="gemini-embedding-001")
    db = FAISS.load_local(VECTORSTORE_DIR, embeddings, allow_dangerous_deserialization=True)
    retriever = db.as_retriever(search_kwargs={"k": 3})
    llm = ChatGoogleGenerativeAI(model="gemini-2.5-flash", temperature=0.7, stream=True)
    return llm, retriever

# --- MAIN APP INTERFACE ---
st.title("🎓 Your Personal Study Assistant")

# --- SIDEBAR ---
with st.sidebar:
    st.title("⚙️ Controls & Profile")
    st.info("Manage your knowledge base, and profile here.")

    google_api_key = get_google_api_key()

    st.caption("Uploaded documents and profile data use temporary local storage. They can reset after a Cloud restart or redeploy.")

    with st.expander("📚 Add to Knowledge Base"):
        uploaded_files = st.file_uploader("Upload PDF files", type=["pdf"], accept_multiple_files=True)
        if st.button("Process Files"):
            if not uploaded_files: st.warning("Please upload at least one PDF file.")
            elif not google_api_key: st.error("Please enter your Google API Key.")
            else:
                with st.spinner("Embedding documents..."):
                    os.environ["GOOGLE_API_KEY"] = google_api_key
                    embeddings = GoogleGenerativeAIEmbeddings(model="gemini-embedding-001")
                    process_and_store_documents(uploaded_files, embeddings)
                    st.cache_resource.clear()

    with st.expander("👤 Your Profile"):
        user_profile = load_profile()
        user_name = st.text_input("Your Name:", value=user_profile.get("name", ""))
        user_notes = st.text_area("Your Study Notes:", value=user_profile.get("notes", ""), height=150)
        if st.button("Save Profile"):
            user_profile["name"] = user_name
            user_profile["notes"] = user_notes
            save_profile(user_profile)
            st.success("Profile saved!")
            st.rerun()

    # --- NEW: PROGRESS TRACKER UI ---
    with st.expander("🎯 Progress Tracker", expanded=True):
        user_profile = load_profile()
        
        # Add new task
        new_task = st.text_input("Add a new chapter or task:")
        if st.button("Add Task"):
            if new_task:
                user_profile["tasks"].append({"description": new_task, "status": "Not Started"})
                save_profile(user_profile)
                st.success(f"Added task: {new_task}")
                st.rerun()
        
        # Display tasks and progress
        if user_profile["tasks"]:
            st.markdown("---")
            completed_count = 0
            total_tasks = len(user_profile["tasks"])

            # Use a copy for iteration while modifying
            for i, task in enumerate(user_profile["tasks"]):
                col1, col2, col3 = st.columns([0.6, 0.3, 0.1])
                with col1:
                    st.markdown(f"**{i+1}.** {task['description']}")
                with col2:
                    # Unique key for each selectbox
                    status_options = ["Not Started", "In Progress", "Completed"]
                    current_status_index = status_options.index(task['status'])
                    new_status = st.selectbox(
                        "Status", status_options, 
                        index=current_status_index, 
                        key=f"status_{i}"
                    )
                    # If status changes, update the profile
                    if new_status != task['status']:
                        user_profile['tasks'][i]['status'] = new_status
                        save_profile(user_profile)
                        st.rerun()
                with col3:
                    # Unique key for each delete button
                    if st.button("🗑️", key=f"del_{i}"):
                        user_profile["tasks"].pop(i)
                        save_profile(user_profile)
                        st.rerun()
                
                if task["status"] == "Completed":
                    completed_count += 1
            
            st.markdown("---")
            st.write(f"**Overall Progress:** {completed_count} of {total_tasks} tasks completed.")
            if total_tasks > 0:
                progress_percent = completed_count / total_tasks
                st.progress(progress_percent)
        else:
            st.info("No tasks added yet. Add a task above to start tracking!")


    if st.button("Clear Chat History"):
        st.session_state.clear()
        st.success("Chat cleared!")
        st.rerun()

# --- CHAT INTERFACE ---

if not google_api_key: st.error("Google API key is not configured. Add GOOGLE_API_KEY to Streamlit Secrets and restart the app."); st.stop()
if not os.path.exists(VECTORSTORE_DIR): st.info("Knowledge base is empty. Upload PDFs and 'Process Files'."); st.stop()

try: llm, retriever = load_components(google_api_key)
except Exception as e: st.error(f"Failed to load. Have you processed files? Error: {e}"); st.stop()

if "messages" not in st.session_state:
    st.session_state.messages = []
    user_name = load_profile().get("name", "Student")
    st.session_state.messages.append({"role": "assistant", "content": f"Hello {user_name}! How can I help?"})

for message in st.session_state.messages:
    with st.chat_message(message["role"]): st.markdown(message["content"])

if user_query := st.chat_input("Ask a question..."):
    st.session_state.messages.append({"role": "user", "content": user_query})
    with st.chat_message("user"): st.markdown(user_query)

    with st.chat_message("assistant"):
        try:
            def stream_response_generator():
                current_profile = load_profile()
                # --- NEW: Prepare progress info for the prompt ---
                tasks = current_profile.get("tasks", [])
                task_summary = "No tasks assigned."
                if tasks:
                    completed = [t['description'] for t in tasks if t['status'] == 'Completed']
                    in_progress = [t['description'] for t in tasks if t['status'] == 'In Progress']
                    not_started = [t['description'] for t in tasks if t['status'] == 'Not Started']
                    task_summary = (
                        f"Completed Tasks: {', '.join(completed) or 'None'}.\n"
                        f"In Progress Tasks: {', '.join(in_progress) or 'None'}.\n"
                        f"Tasks Not Started: {', '.join(not_started) or 'None'}."
                    )

                memory = ConversationBufferWindowMemory(
                    k=5, memory_key="chat_history", return_messages=True, output_key='answer'
                )
                for msg in st.session_state.messages[:-1]:
                    if msg["role"] == "user": memory.chat_memory.add_user_message(msg["content"])
                    else: memory.chat_memory.add_ai_message(msg["content"])

                # --- NEW: Updated Prompt with Progress Tracking ---
                prompt_template = f"""
                You are an expert study assistant for '{current_profile.get('name', 'the user')}'. Your tone should be encouraging, friendly, and helpful.
                You are like a friend to the user that also motivates them sometime to study more and make progress.
                Use the retrieved context, chat history, user's notes, and their study progress to provide a clear answer.
                If the context is not relevant, politely say you don't have information on that topic from the documents and try to help the
                user by using your own knowledge base. Also ask the user relevant questions or numericals for better understanding.
                
                USER'S STUDY NOTES: {current_profile.get('notes', 'N/A')}
                
                USER'S CURRENT STUDY PROGRESS:
                {task_summary}
                
                Based on the user's progress, if they ask what to do next, suggest a task that is 'Not Started' or 'In Progress'. Motivate them based on what they've completed.

                CONTEXT: {{context}}
                CHAT HISTORY: {{chat_history}}
                QUESTION: {{question}}
                ANSWER:
                """
                # This is inside the stream_response_generator function
                QA_CHAIN_PROMPT = PromptTemplate(input_variables=["chat_history", "context", "question"], template=prompt_template)
                # CORRECTED LINE: Using named arguments
                qa_chain = ConversationalRetrievalChain.from_llm(
                llm=llm,
                retriever=retriever,
                memory=memory,
                combine_docs_chain_kwargs={"prompt": QA_CHAIN_PROMPT},
                output_key='answer'

            )
                for chunk in qa_chain.stream({"question": user_query}):
                    yield chunk.get("answer", "")

            full_response = st.write_stream(stream_response_generator)
        except Exception as e:
            st.error(f"An error occurred: {type(e).__name__}: {e}")
            st.exception(e)
            full_response = "Sorry, I ran into a problem. Please try again."

    st.session_state.messages.append({"role": "assistant", "content": full_response})