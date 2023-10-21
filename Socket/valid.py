import cv2
import numpy as np

# Read the image file into a byte array
with open("cat2.jpg", "rb") as image_file:
    image_data = image_file.read()


# Decode the byte array into an image
np_data = np.frombuffer(image_data, dtype=np.uint8)
image = cv2.imdecode(np_data, cv2.IMREAD_COLOR)

# Check if the image is valid
if image is not None:
    print("Valid image")
    # Display the image using OpenCV
    cv2.imshow("Valid Image", image)
    cv2.waitKey(0)
    cv2.destroyAllWindows()
else:
    print("Invalid image")
