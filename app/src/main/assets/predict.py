from ultralytics import YOLO
import cv2
import os
import sys
import json

def predict(image_path):
    try:
        # Load the model
        model = YOLO('model.pt')
        
        # Perform inference
        results = model.predict(source=image_path, save=False, show=False)
        
        # Prepare the result data
        predictions = []
        result_img = None
        
        for result in results:
            # Get the annotated image with bounding boxes
            result_img = result.plot()
            
            for box in result.boxes:
                class_id = int(box.cls[0])
                confidence = float(box.conf[0].item())
                bounding_box = box.xyxy[0].tolist()
                class_name = model.names[class_id] if model.names else f"Class {class_id}"
                
                predictions.append({
                    "class": class_name,
                    "confidence": confidence,
                    "bounding_box": bounding_box
                })
        
        # Save the annotated image
        output_dir = os.path.dirname(image_path)
        base_name = os.path.basename(image_path)
        file_name, ext = os.path.splitext(base_name)
        output_path = os.path.join(output_dir, f'{file_name}_result{ext}')
        cv2.imwrite(output_path, result_img)
        
        # Create the result JSON
        result = {
            "status": "success",
            "predictions": predictions,
            "output_image": output_path
        }
        
        # Print the result as JSON (will be captured by Android)
        print(json.dumps(result))
        return 0
        
    except Exception as e:
        error_result = {
            "status": "error",
            "message": str(e)
        }
        print(json.dumps(error_result))
        return 1

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(json.dumps({"status": "error", "message": "Image path not provided"}))
        sys.exit(1)
    
    image_path = sys.argv[1]
    sys.exit(predict(image_path))
