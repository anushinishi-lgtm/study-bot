import cv2
import time

# Load face detection model
face_cascade = cv2.CascadeClassifier(
    cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
)

# Start webcam
cap = cv2.VideoCapture(0)

focus_start = None
total_focus_time = 0

while True:
    ret, frame = cap.read()
    
    if not ret:
        break

    # Convert to grayscale for face detection
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Detect faces
    faces = face_cascade.detectMultiScale(
        gray,
        scaleFactor=1.3,
        minNeighbors=5
    )

    # Check if user present
    user_present = len(faces) > 0

    # Focus tracking logic
    if user_present:
        if focus_start is None:
            focus_start = time.time()
    else:
        if focus_start is not None:
            total_focus_time += time.time() - focus_start
            focus_start = None

    # Display focus time
    focus_display = int(total_focus_time)

    cv2.putText(
        frame,
        f"Focus Time: {focus_display}s",
        (20, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        1,
        (0, 255, 0),
        2
    )

    # Display status
    status = "Focused" if user_present else "Away"

    cv2.putText(
        frame,
        f"Status: {status}",
        (20, 80),
        cv2.FONT_HERSHEY_SIMPLEX,
        1,
        (255, 0, 0),
        2
    )

    # Show camera
    cv2.imshow("Focus Companion", frame)

    # Keyboard controls
    key = cv2.waitKey(1) & 0xFF

    # Close using q or ESC
    if key == ord("q") or key == 27:
        break

    # Close using X button
    try:
        if cv2.getWindowProperty("Focus Companion", cv2.WND_PROP_VISIBLE) < 1:
            break
    except:
        break


# Release camera
cap.release()
cv2.destroyAllWindows()