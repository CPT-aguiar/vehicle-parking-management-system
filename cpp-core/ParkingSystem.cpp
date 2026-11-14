#include <iostream>
#include <vector>
#include <string>
#include <algorithm>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <cstring>

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/mman.h>
#include <unistd.h>
#endif

using namespace std;

enum class SpaceType { MOTORCYCLE, COMPACT, LARGE };
enum class VehicleType { MOTORCYCLE = 1, CAR = 2, BUS = 3 };

class vehicle {
protected:
    string plate;
    VehicleType type;
    int level;
    int row;
    int startBay;

public:
    vehicle(string p, VehicleType t) : plate(p), type(t), level(-1), row(-1), startBay(-1) {}
    virtual ~vehicle() {}

    string getPlate() const { return plate; }
    VehicleType getType() const { return type; }
    string getTypeName() const {
        if (type == VehicleType::MOTORCYCLE) return "Motorcycle";
        if (type == VehicleType::CAR) return "Car";
        return "Bus";
    }

    void setLocation(int lvl, int r, int bay) {
        level = lvl;
        row = r;
        startBay = bay;
    }

    string getLocationString() const {
        if (level == -1) return "Not Parked";
        return "Level " + to_string(level) + ", Row " + to_string(row) + ", Bay " + to_string(startBay);
    }

    virtual int getRequiredSpaces() const = 0;
    virtual bool canFit(SpaceType sType) const = 0;

    class motorcycles;
    class cars;
    class buses;
};

class vehicle::motorcycles : public vehicle {
public:
    motorcycles(string p) : vehicle(p, VehicleType::MOTORCYCLE) {}
    int getRequiredSpaces() const override { return 1; }
    bool canFit(SpaceType sType) const override { return true; }
};

class vehicle::cars : public vehicle {
public:
    cars(string p) : vehicle(p, VehicleType::CAR) {}
    int getRequiredSpaces() const override { return 1; }
    bool canFit(SpaceType sType) const override {
        return (sType == SpaceType::COMPACT || sType == SpaceType::LARGE);
    }
};

class vehicle::buses : public vehicle {
public:
    buses(string p) : vehicle(p, VehicleType::BUS) {}
    int getRequiredSpaces() const override { return 5; }
    bool canFit(SpaceType sType) const override {
        return (sType == SpaceType::LARGE);
    }
};

struct BayNode {
    int bayId;
    SpaceType type;
    bool occupied;
    string vehiclePlate;
    BayNode* prev;
    BayNode* next;

    BayNode(int id, SpaceType t)
        : bayId(id), type(t), occupied(false), vehiclePlate(""), prev(nullptr), next(nullptr) {}
};

class RowDoublyLinkedList {
public:
    BayNode* head;
    BayNode* tail;

    RowDoublyLinkedList() : head(nullptr), tail(nullptr) {}

    void appendBay(int id, SpaceType type) {
        BayNode* newNode = new BayNode(id, type);
        if (!head) {
            head = tail = newNode;
        } else {
            tail->next = newNode;
            newNode->prev = tail;
            tail = newNode;
        }
    }

    int allocateVehicle(vehicle* v) {
        BayNode* curr = head;
        int needed = v->getRequiredSpaces();

        while (curr != nullptr) {
            if (!curr->occupied && v->canFit(curr->type)) {
                if (needed == 1) {
                    curr->occupied = true;
                    curr->vehiclePlate = v->getPlate();
                    return curr->bayId;
                } else if (needed == 5) {
                    BayNode* check = curr;
                    int count = 0;
                    while (check != nullptr && count < 5 && !check->occupied && check->type == SpaceType::LARGE) {
                        count++;
                        check = check->next;
                    }
                    if (count == 5) {
                        BayNode* fill = curr;
                        for (int i = 0; i < 5; i++) {
                            fill->occupied = true;
                            fill->vehiclePlate = v->getPlate();
                            fill = fill->next;
                        }
                        return curr->bayId;
                    }
                }
            }
            curr = curr->next;
        }
        return -1;
    }
};

struct BSTNode {
    int bayGlobalId;
    string plate;
    BSTNode* left;
    BSTNode* right;
    BSTNode(int id, string p) : bayGlobalId(id), plate(p), left(nullptr), right(nullptr) {}
};

class ParkingBST {
private:
    BSTNode* root = nullptr;
    BSTNode* insertRec(BSTNode* node, int id, string plate) {
        if (!node) return new BSTNode(id, plate);
        if (id < node->bayGlobalId) node->left = insertRec(node->left, id, plate);
        else if (id > node->bayGlobalId) node->right = insertRec(node->right, id, plate);
        return node;
    }
    void inorderRec(BSTNode* node) {
        if (!node) return;
        inorderRec(node->left);
        cout << "  [BST Bay #" << node->bayGlobalId << "] -> Plate: " << node->plate << endl;
        inorderRec(node->right);
    }
public:
    void insert(int id, string plate) { root = insertRec(root, id, plate); }
    void displayInOrder() { inorderRec(root); }
};

class VehicleHashTable {
private:
    static const int TABLE_SIZE = 31;
    vector<pair<string, vehicle*>> buckets[TABLE_SIZE];

    int hashFunction(const string& key) {
        unsigned long hash = 5381;
        for (char c : key) hash = ((hash << 5) + hash) + c;
        return hash % TABLE_SIZE;
    }
public:
    void insert(vehicle* v) {
        int idx = hashFunction(v->getPlate());
        buckets[idx].push_back({v->getPlate(), v});
    }

    vehicle* lookup(const string& plate) {
        int idx = hashFunction(plate);
        for (auto& entry : buckets[idx]) {
            if (entry.first == plate) return entry.second;
        }
        return nullptr;
    }
};

class BinarySearch {
public:
    static vehicle* searchByPlate(const vector<vehicle*>& sortedVehicles, const string& targetPlate) {
        int left = 0, right = (int)sortedVehicles.size() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (sortedVehicles[mid]->getPlate() == targetPlate)
                return sortedVehicles[mid];
            if (sortedVehicles[mid]->getPlate() < targetPlate)
                left = mid + 1;
            else
                right = mid - 1;
        }
        return nullptr;
    }
};

class MergeSort {
public:
    static bool compareByType(const vehicle* a, const vehicle* b) {
        if (a->getType() != b->getType())
            return static_cast<int>(a->getType()) < static_cast<int>(b->getType());
        return a->getPlate() < b->getPlate();
    }

    static void sortVehicles(vector<vehicle*>& arr, int left, int right) {
        if (left >= right) return;
        int mid = left + (right - left) / 2;
        sortVehicles(arr, left, mid);
        sortVehicles(arr, mid + 1, right);
        merge(arr, left, mid, right);
    }

private:
    static void merge(vector<vehicle*>& arr, int left, int mid, int right) {
        vector<vehicle*> temp;
        int i = left, j = mid + 1;
        while (i <= mid && j <= right) {
            if (compareByType(arr[i], arr[j])) temp.push_back(arr[i++]);
            else temp.push_back(arr[j++]);
        }
        while (i <= mid) temp.push_back(arr[i++]);
        while (j <= right) temp.push_back(arr[j++]);
        for (int k = 0; k < (int)temp.size(); k++) arr[left + k] = temp[k];
    }
};

struct SharedMemoryBuffer {
    int totalOccupied;
    char lastEvent[64];
};

void runIPCDemo() {
    cout << "\n--- IPC Producer-Consumer Demo (Shared Memory + Message Passing) ---" << endl;
#ifdef _WIN32
    SharedMemoryBuffer* shm = (SharedMemoryBuffer*)VirtualAlloc(NULL, sizeof(SharedMemoryBuffer), MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
#else
    SharedMemoryBuffer* shm = (SharedMemoryBuffer*)mmap(NULL, sizeof(SharedMemoryBuffer), PROT_READ | PROT_WRITE, MAP_SHARED | MAP_ANONYMOUS, -1, 0);
#endif
    shm->totalOccupied = 0;
    strcpy(shm->lastEvent, "INIT");

    queue<string> messageQueue;
    mutex mtx;
    condition_variable cv;
    bool done = false;

    thread producer([&]() {
        vector<string> events = {"ENTRY:MOTO-01", "ENTRY:CAR-99", "ENTRY:BUS-77"};
        for (const auto& msg : events) {
            {
                lock_guard<mutex> lock(mtx);
                messageQueue.push(msg);
                cout << "[Producer Gate] Sent Message: " << msg << endl;
            }
            cv.notify_one();
            this_thread::sleep_for(chrono::milliseconds(50));
        }
        {
            lock_guard<mutex> lock(mtx);
            done = true;
        }
        cv.notify_one();
    });

    thread consumer([&]() {
        while (true) {
            unique_lock<mutex> lock(mtx);
            cv.wait(lock, [&]() { return !messageQueue.empty() || done; });
            while (!messageQueue.empty()) {
                string msg = messageQueue.front();
                messageQueue.pop();
                shm->totalOccupied++;
                strncpy(shm->lastEvent, msg.c_str(), 63);
                cout << "[Consumer Controller] Processed '" << msg
                     << "' | SharedMem Occupied Count = " << shm->totalOccupied << endl;
            }
            if (done) break;
        }
    });

    producer.join();
    consumer.join();

#ifdef _WIN32
    VirtualFree(shm, 0, MEM_RELEASE);
#else
    munmap(shm, sizeof(SharedMemoryBuffer));
#endif
}

int main() {
    RowDoublyLinkedList level1Row1;
    for (int i = 1; i <= 2; i++) level1Row1.appendBay(i, SpaceType::MOTORCYCLE);
    for (int i = 3; i <= 5; i++) level1Row1.appendBay(i, SpaceType::COMPACT);
    for (int i = 6; i <= 10; i++) level1Row1.appendBay(i, SpaceType::LARGE);

    ParkingBST bayTree;
    VehicleHashTable hashTable;
    vector<vehicle*> parkedList;

    vehicle* v1 = new vehicle::buses("BUS-500");
    vehicle* v2 = new vehicle::motorcycles("MOTO-12");
    vehicle* v3 = new vehicle::cars("CAR-304");

    vector<vehicle*> incoming = {v1, v2, v3};
    for (auto* v : incoming) {
        int assignedBay = level1Row1.allocateVehicle(v);
        if (assignedBay != -1) {
            v->setLocation(1, 1, assignedBay);
            bayTree.insert(100 + assignedBay, v->getPlate());
            hashTable.insert(v);
            parkedList.push_back(v);
            cout << "Parked " << v->getTypeName() << " [" << v->getPlate() << "] at " << v->getLocationString() << endl;
        }
    }

    MergeSort::sortVehicles(parkedList, 0, (int)parkedList.size() - 1);
    cout << "\n--- Vehicles Sorted by Type (Merge Sort) ---" << endl;
    for (auto* v : parkedList) {
        cout << v->getTypeName() << " - " << v->getPlate() << " (" << v->getLocationString() << ")" << endl;
    }

    vector<vehicle*> plateSorted = parkedList;
    sort(plateSorted.begin(), plateSorted.end(), [](vehicle* a, vehicle* b) {
        return a->getPlate() < b->getPlate();
    });

    vehicle* foundBS = BinarySearch::searchByPlate(plateSorted, "CAR-304");
    cout << "\n[Binary Search] CAR-304 found at: " << (foundBS ? foundBS->getLocationString() : "Not Found") << endl;

    vehicle* foundHash = hashTable.lookup("BUS-500");
    cout << "[Hash Table O(1)] BUS-500 found at: " << (foundHash ? foundHash->getLocationString() : "Not Found") << endl;

    cout << "\n--- Binary Search Tree In-Order Bay Index ---" << endl;
    bayTree.displayInOrder();

    runIPCDemo();

    for (auto* v : incoming) delete v;
    return 0;
}
