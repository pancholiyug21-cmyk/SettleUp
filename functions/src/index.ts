import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();

/**
 * Placeholder for future Razorpay integration.
 * DO NOT IMPLEMENT RAZORPAY IN THIS PHASE.
 * 
 * This function will eventually receive an intent to pay from the client,
 * validate the amount against the outstanding balance, and call Razorpay APIs
 * to generate a secure Order ID to return to the Android client.
 */
export const createPaymentOrder = functions.https.onCall(async (data, context) => {
    // 1. Verify Authentication
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }
    
    // 2. Validate input parameters securely
    
    // 3. (Future) Generate Razorpay Order
    
    return {
        success: false,
        message: "Not implemented in Phase 1"
    };
});

/**
 * Placeholder for future backend payment verification.
 * 
 * This webhook will be called directly by Razorpay when a payment succeeds.
 * It is the ONLY trusted actor capable of changing a payment/settlement 
 * status to COMPLETED or VERIFIED in Firestore.
 */
export const razorpayWebhook = functions.https.onRequest(async (req, res) => {
    // 1. Verify webhook signature using Razorpay secret
    
    // 2. Update Firestore payment status to VERIFIED
    
    // 3. Create or update Settlement record as COMPLETED
    
    res.status(200).send("Not implemented in Phase 1");
});

/**
 * Securely joins a user to a group using an invite code.
 * 
 * Since the Firestore rules prevent clients from writing directly to the
 * members collection (to prevent unauthorized joins), this function acts
 * as the trusted middleman to validate the invite code and add the user.
 */
export const joinGroupWithCode = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
    }

    const inviteCode = data.inviteCode;
    if (!inviteCode || typeof inviteCode !== "string") {
        throw new functions.https.HttpsError("invalid-argument", "Valid inviteCode is required.");
    }

    const uid = context.auth.uid;

    try {
        // Query for the group with this invite code
        const groupsRef = db.collection("groups");
        const querySnapshot = await groupsRef.where("inviteCode", "==", inviteCode.toUpperCase()).limit(1).get();

        if (querySnapshot.empty) {
            throw new functions.https.HttpsError("not-found", "Invalid invite code.");
        }

        const groupDoc = querySnapshot.docs[0];
        const groupId = groupDoc.id;

        // Fetch the user's details to store in the members collection
        const userDoc = await db.collection("users").doc(uid).get();
        if (!userDoc.exists) {
            throw new functions.https.HttpsError("failed-precondition", "User profile not found.");
        }
        
        const userData = userDoc.data();
        const displayName = userData?.displayName || "Unknown User";

        // Add the user to the members subcollection
        const memberRef = db.collection(`groups/${groupId}/members`).doc(uid);
        
        await memberRef.set({
            uid: uid,
            role: "MEMBER",
            displayName: displayName,
            joinedAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // Also add the uid to the memberIds array on the group doc for easier querying
        await db.collection("groups").doc(groupId).update({
            memberIds: admin.firestore.FieldValue.arrayUnion(uid)
        });

        return {
            success: true,
            groupId: groupId
        };
    } catch (error) {
        console.error("Error joining group:", error);
        throw new functions.https.HttpsError("internal", "An error occurred while joining the group.");
    }
});
