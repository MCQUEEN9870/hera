# Supabase Storage Folder Structure for Vehicle Images

## Overview

This document outlines the new approach to storing vehicle images in Supabase Storage.

### Previous System (Flat Structure)
- All images were stored directly in the `vehicle-images` bucket without organization
- Full image URLs were stored in the `registration_image_urls` table 
- Difficult to track which images belonged to which registration

### New System (Folder Structure)
- Images are organized in folders named after registration IDs
  - Example: `vehicle-images/11/some-image.png`
- Only the folder path (registration ID) is stored in the database
- Each registration has all its images in one dedicated folder

## Database Structure

**New Table: `registration_image_folders`**

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| registration_id | BIGINT | Foreign key to registration table |
| folder_path | VARCHAR | Folder path in storage (usually just the registration ID) |
| created_at | TIMESTAMP | When this record was created |

## API Endpoints

### Upload Images to a Registration Folder

```
POST /api/registration-images/{registrationId}
```

**Parameters:**
- `images`: Array of image files (multipart form data)

**Response:**
```json
{
  "success": true,
  "message": "Images uploaded successfully",
  "registrationId": 11,
  "folderPath": "11",
  "imageUrls": [
    "https://example.supabase.co/storage/v1/object/public/vehicle-images/11/image1.png",
    "https://example.supabase.co/storage/v1/object/public/vehicle-images/11/image2.png"
  ]
}
```

### Get Images for a Registration

```
GET /api/registration-images/{registrationId}
```

**Response:**
```json
{
  "success": true,
  "registrationId": 11,
  "folderPath": "11",
  "imageUrls": [
    "https://example.supabase.co/storage/v1/object/public/vehicle-images/11/image1.png",
    "https://example.supabase.co/storage/v1/object/public/vehicle-images/11/image2.png"
  ]
}
```

### Delete All Images for a Registration

```
DELETE /api/registration-images/{registrationId}
```

**Response:**
```json
{
  "success": true,
  "message": "All images deleted successfully",
  "registrationId": 11
}
```

## Migration from Old System

To migrate existing registrations from the flat structure to the new folder structure:

```
POST /api/migration/images
```

This will migrate all registrations and return status for each one.

To migrate a specific registration:

```
POST /api/migration/images/{registrationId}
```

## Implementation Details

1. When registering a new vehicle:
   - System creates a registration record first to get an ID
   - Images are uploaded to a folder named after the registration ID
   - The folder path is stored in `registration_image_folders` table

2. For fetching images:
   - Get folder path from `registration_image_folders` table
   - List all objects in that folder from Supabase Storage

3. For deleting images:
   - Delete all objects in the folder
   - Remove the folder record from database

## Benefits

1. **Organization**: Images are logically grouped by registration
2. **Simplicity**: Only need to store one folder path per registration, not individual URLs
3. **Maintenance**: Easier to manage, backup, or delete all images for a specific registration
4. **Scalability**: Works better as the number of registrations and images grows

## Code Example: Uploading Images

```java
// Example using the new endpoints
Long registrationId = 11L;
MultipartFile[] images = /* your image files */;

// Call the API
ResponseEntity<?> response = registrationImageController.uploadImagesToFolder(
    registrationId, 
    images
);
```

# Image Storage in Hera Pheri Goods Application

## Overview

The application uses Supabase Storage for managing images with the following buckets:

- `vehicle-images`: Stores vehicle images organized by registration ID folders
- `profile-photos`: Stores user profile photos
- `deleted-images`: Archive bucket for deleted vehicle images
- `deleted-profiles`: Archive bucket for deleted user profile photos

## Image Archiving Functionality

When a user deletes a vehicle or their entire account, the application performs intelligent image archiving:

### Vehicle Deletion

1. Images are moved from `vehicle-images/<registration_id>/` to `deleted-images/`
2. Files are renamed in the archive bucket with a format: `reg<registration_id>_<original_filename>`
3. Original images are deleted after successful archiving
4. The vehicle data is deleted from the database

### User Account Deletion

1. Profile photo is moved from `profile-photos/{userId}.png` to `deleted-profiles/user<userId>_profile_<original_filename>`
2. All vehicles linked to the user have their images archived following the vehicle deletion process
3. All original images are deleted after successful archiving
4. The user account and vehicle data are deleted from the database

## API Endpoints

### Delete Vehicle with Image Archiving

```
DELETE /api/deleteVehicle/{registrationId}
```

Parameters:
- `registrationId`: ID of the vehicle registration to delete

Response:
```json
{
  "success": true,
  "message": "Vehicle successfully deleted"
}
```

### Delete User Account with Image Archiving

```
DELETE /api/deleteUser/{userId}
```

Parameters:
- `userId`: ID of the user to delete

Response:
```json
{
  "success": true,
  "message": "User account and all associated data successfully deleted"
}
```

## Error Handling

- The system attempts to archive all images but will continue with deletion even if some archiving operations fail
- Failed operations are logged but don't prevent the deletion process
- If a single image archive fails, other images continue to be processed

## Implementation Details

- Uses Supabase Java SDK (via HTTP API) for upload/delete operations
- Archive operations are performed as atomic transactions where possible
- Images are first copied to the archive bucket before deleting the originals
- All operations are logged for debugging and auditing 