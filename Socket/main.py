import socket
import threading
import cv2
import queue
import numpy as np
from PIL import Image



imageQueue = queue.Queue()

# Define the start and end markers
start_marker = b"FRAME_START"
buffer_size = 270011
valid_frame_size = 270000
image_width = 300
image_height = 300

def process_frame(frame):
    print(f"Received a frame successfully, frame_size = {len(frame)} bytes")

    # # Decode the recieved bytes into an image and display it
    np_data = np.frombuffer(frame, dtype=np.uint8)
    # Create a PIL Image object from the raw image data
    image = Image.frombytes('RGB', (image_width, image_height), np_data)

    return image

def clientLoop():
    frame = bytearray()
    while True:
        image = None
        start_index = -1
        # Receive data from the client
        data = client_socket.recv(buffer_size)

        if not data:
            break

        start_index = data.find(start_marker)
        if start_index != -1:
            print("found start marker")
            # extend the rest of the frame if any
            frame.extend(data[:start_index])
            if len(frame) == valid_frame_size:
                image = process_frame(frame)
            frame = bytearray()
            frame.extend(data[start_index+len(start_marker):])
        else:
            frame.extend(data)

        if image is not None:
            # Convert the PIL Image to a NumPy array
            image_np = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)

            # Display the image using OpenCV
            cv2.imshow('Received Frames', image_np)

            # Check for the 'q' key to quit
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break

    # Release OpenCV window and cleanup
    cv2.destroyAllWindows()
    client_socket.close()

# Create a socket
server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, buffer_size)


# Bind the socket to a specific address and port
host = '0.0.0.0'  # Listen on all available network interfaces
port = 8000
server_socket.bind((host, port))

# Listen for incoming connections
server_socket.listen(1)  # Allow only one client to connect at a time


while True:
    print(f"Server is listening on {host}:{port}")

    # Accept a client connection
    client_socket, client_address = server_socket.accept()
    print(f"Accepted connection from {client_address}")

    clientThread = threading.Thread(target=clientLoop)
    clientThread.start()



