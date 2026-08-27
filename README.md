# Catgram 🐱

Catgram is a pet Android project: a small social feed app for cat lovers.  
Users can browse cat photos, create posts, and publish only cat-related images.

The project was built to practice modern Android development, external API integration, and AI-based image validation.

📱 Published in RuStore: [Catgram on RuStore](https://www.rustore.ru/catalog/app/com.mobdev.catgram)

---

## Overview

Catgram focuses on a simple idea: a social feed where every post must contain a cat.

To keep the content relevant, the app uses AI-based image validation. When a user creates a post, the selected image is checked before publishing. If the image does not contain a cat, the post is rejected.

---

## Features

- Cat photo feed
- Post creation flow
- External Cats API integration
- AI-based cat image validation
- Jetpack Compose UI
- Kotlin-based Android implementation

---

## Tech Stack

- **Kotlin**
- **Android SDK**
- **Jetpack Compose**
- **Coroutines**
- **REST API**
- **Firebase**
- **AI image filtering**

## Screenshots

<p align="center">
  <img width="250" alt="Feed screen" src="https://github.com/user-attachments/assets/615eddd1-e8d1-4efc-9e5c-95fe31834eea" />
  <img width="250" alt="Create post screen" src="https://github.com/user-attachments/assets/56191c3e-f9a5-41ca-92df-67743e0567f7" />
  <img width="250" alt="Filter screen" src="https://github.com/user-attachments/assets/73ae4fcf-89f0-454b-b6ae-7d5062edb203" />
</p>

---

### Firebase deployment notes

- Favourites are migrated lazily from the legacy arrays in `users/{uid}` to `users/{uid}/favourites_v2/{type:itemId}`. The migration is idempotent and sets `favouritesSchemaVersion = 2` only after all documents are written.
- Existing Firestore rules must allow each authenticated user to read and write only their own `favourites_v2` subcollection. Post ownership remains enforced by the existing `user_posts` rule.
- Post images continue to be uploaded to ImgBB. Deleting a post removes its Firestore data but does not delete the ImgBB image.

