#include <jni.h>
#include <vector>
#include "opencv2/imgproc.hpp"

#define RESIZE_HEIGHT 250
#define APPROX_POLY_EPSILON 0.02
#define THRUSH1 0
#define THRUSH2 50
#define N_SHIFTS 11
#define NEAR_DIST 10
#define NEAR_QUAD 50

using namespace cv;

double resize_fit_height(Mat &img, int height) {
    double ratio = 1.0 * img.rows / height;
    resize(img, img, Size(static_cast<int>(img.cols / ratio), height), CV_INTER_AREA);
    return ratio;
}

double distance(Point &p1, Point &p2) {
    return sqrt(((p1.x - p2.x) * (p1.x - p2.x)) + ((p1.y - p2.y) * (p1.y - p2.y)));
}

bool isNear(Point &p1, Point p2, double dst) {
    return distance(p1, p2) < dst;
}

bool cornersAreNotBordered(std::vector<Point> &points, const Mat &mat) {
    for (Point &p : points) {
        if (p.x < NEAR_DIST || p.x > mat.cols - NEAR_DIST - 1 ||
            p.y < NEAR_DIST || p.y > mat.rows - NEAR_DIST - 1) {
            return false;
        }
    }
    return true;
}

bool cornersAreNotCriticalCorners(std::vector<Point> &points, const Mat &mat) {
    for (Point &p : points) {
        if (isNear(p, Point{0, 0}, NEAR_DIST)   /*(p.x == 0 && p.y == 0)*/
            || isNear(p, Point{mat.cols - 1, 0}, NEAR_DIST) /*(p.x == mat.cols - 1 && p.y == 0)*/
            || isNear(p, Point{0, mat.rows - 1}, NEAR_DIST) /*(p.x == 0 && p.y == mat.rows -1)*/
            || isNear(p, Point{mat.cols - 1, mat.rows - 1},
                      NEAR_DIST) /*(p.x == mat.cols - 1 && p.y == mat.rows -1))*/) {
            return false;
        }
    }
    return true;
}


// helper function:
// finds a cosine of angle between std::vectors
// from pt0->pt1 and from pt0->pt2
double cosine(Point &pt1, Point &pt2, Point &pt0) {
    double dx1 = pt1.x - pt0.x;
    double dy1 = pt1.y - pt0.y;
    double dx2 = pt2.x - pt0.x;
    double dy2 = pt2.y - pt0.y;
    return (dx1 * dx2 + dy1 * dy2) /
           sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
}


struct x_sort {
    bool operator()(Point2f a, Point2f b) {
        return a.x < b.x;
    }
} x_sort;

void findSquares(Mat &image, std::vector<std::vector<Point> > &squares) {
    squares.clear();
    Mat gray0(image.size(), CV_8U), gray;

    Size imgSize = image.size();
    pyrDown(image, image, Size(image.cols / 2, image.rows / 2));
    pyrUp(image, image, imgSize);
    std::vector<std::vector<Point> > contours;

    for (int c = 0; c < 3; c++) {
        int ch[] = {c, 0};
        mixChannels(&image, 1, &gray0, 1, ch, 1);

        for (int l = 0; l < N_SHIFTS; l++) {
            if (l == 0) {
                Canny(gray0, gray, THRUSH1, THRUSH2);
                dilate(gray, gray, Mat(), Point(-1, -1));
            } else {
                gray = gray0 >= (l + 1) * 255 / N_SHIFTS;
            }

            findContours(gray, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);
            std::vector<Point> approx;

            for (const auto &contour : contours) {
                approxPolyDP(Mat(contour), approx,
                             arcLength(Mat(contour), true) * APPROX_POLY_EPSILON, true);

                if (approx.size() == 4 &&
                    fabs(contourArea(Mat(approx))) > 1000 &&
                    isContourConvex(Mat(approx))) {
                    double maxCosine = 0;

                    for (int j = 2; j < 5; j++) {
                        maxCosine = MAX(maxCosine,
                                        fabs(cosine(approx[j % 4], approx[j - 2], approx[j - 1])));
                    }

                    if (maxCosine < 0.4 && cornersAreNotCriticalCorners(approx, image)) {
                        squares.push_back(approx);
                    }
                }
            }
        }
    }
}


void sort_quad_clockwise(std::vector<Point> &quad) {
    std::sort(quad.begin(), quad.end(), x_sort);
    if (quad[0].y > quad[1].y) {
        swap(quad[0], quad[1]);
    }
    if (quad[2].y > quad[3].y) {
        swap(quad[2], quad[3]);
    }
    // {tl, bl, tr, br}
    swap(quad[1], quad[2]); // todo czy wystarcczy tak?? -> nie todo
    swap(quad[2], quad[3]); // todo czy wystarcczy tak?? -> nie todo
    // {tl, tr, br, bl}
};

void sortSquares(std::vector<std::vector<Point>> &squares) {
    for (std::vector<Point> &square : squares) {
        sort_quad_clockwise(square);
    }
}

void avgSquares(std::vector<std::vector<Point>> &squares, int weight[]) {
    for (int i = 0; i < squares.size(); ++i) {
        if (weight[i] > 0) {
            for (int j = 0; j < squares.size(); ++j) {
                if (i != j && weight[j] > 0) {
                    if (isNear(squares[i][0], squares[j][0], NEAR_QUAD) &&
                        isNear(squares[i][1], squares[j][1], NEAR_QUAD) &&
                        isNear(squares[i][2], squares[j][2], NEAR_QUAD) &&
                        isNear(squares[i][3], squares[j][3], NEAR_QUAD)) {
                        squares[i][0].x = static_cast<int>(
                                (weight[i] * squares[i][0].x + weight[j] * squares[j][0].x) /
                                (double) (weight[i] + weight[j]));
                        squares[i][0].y = static_cast<int>(
                                (weight[i] * squares[i][0].y + weight[j] * squares[j][0].y) /
                                (double) (weight[i] + weight[j]));

                        squares[i][1].x = static_cast<int>(
                                (weight[i] * squares[i][1].x + weight[j] * squares[j][1].x) /
                                (double) (weight[i] + weight[j]));
                        squares[i][1].y = static_cast<int>(
                                (weight[i] * squares[i][1].y + weight[j] * squares[j][1].y) /
                                (double) (weight[i] + weight[j]));

                        squares[i][2].x = static_cast<int>(
                                (weight[i] * squares[i][2].x + weight[j] * squares[j][2].x) /
                                (double) (weight[i] + weight[j]));
                        squares[i][2].y = static_cast<int>(
                                (weight[i] * squares[i][2].y + weight[j] * squares[j][2].y) /
                                (double) (weight[i] + weight[j]));

                        squares[i][3].x = static_cast<int>(
                                (weight[i] * squares[i][3].x + weight[j] * squares[j][3].x) /
                                (double) (weight[i] + weight[j]));
                        squares[i][3].y = static_cast<int>(
                                (weight[i] * squares[i][3].y + weight[j] * squares[j][3].y) /
                                (double) (weight[i] + weight[j]));

                        weight[i] += weight[j];
                        weight[j] = -1;
                    }
                }
            }
        }
    }

}

bool findBiggestSquere(std::vector<std::vector<Point>> &squares, const int weight[],
                       std::vector<Point> &biggest) {
    bool found = false;
    double maxContourArea = -1;
    for (int i = 0; i < squares.size(); ++i) {
        if (weight[i] > 1) { //! important !
            if (!found || maxContourArea < fabs(contourArea(Mat(squares[i])))) {
                found = true;
                biggest = squares[i];
                maxContourArea = fabs(contourArea(Mat(squares[i])));
            }
        }
    }
    return found;
}

void throwJavaException(JNIEnv *env, const char *msg) {
    // You can put your own exception here
//    jclass c = env->FindClass("company/com/YourException");

//    if (NULL == c)
//    {
//        B plan: null pointer ...
    jclass c = env->FindClass("java/lang/NullPointerException");
//    }

    env->ThrowNew(c, msg);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_gaspard_scanshine_views_CameraView_decode(JNIEnv *env, jclass type,
                                                   jbyteArray yuv420sp_, jint width, jint height,
                                                   jintArray arr_) {

    jbyte *yuv420sp = env->GetByteArrayElements(yuv420sp_, NULL);
    jint *arr = env->GetIntArrayElements(arr_, NULL);

    if (yuv420sp != NULL && arr != NULL) {
        Mat yuv(height + height / 2, width, CV_8UC1, (uchar *) yuv420sp);
        cv::Mat mImage;//(/*height, width, CV_8UC3*/);
        cv::cvtColor(yuv, mImage, CV_YUV2BGR_NV21);
//        cvtColor(yuv, mImage, CV_YUV2RGBA_NV21/*COLOR_YUV420sp2RGBA*/);

        // TODO DEBUG SIGSEGV IN SOME DEVICES HERE
        int rows = mImage.rows, cols = mImage.cols;
        double ratio = resize_fit_height(mImage, RESIZE_HEIGHT);
        std::vector<std::vector<Point> > squares;
        findSquares(mImage, squares);
        sortSquares(squares);

        int weight[squares.size()];
        for (int k = 0; k < squares.size(); ++k) {
            weight[k] = 1; //usuniety wtw weight <= 0
        }

        avgSquares(squares, weight);

        std::vector<Point> biggest;
        if (findBiggestSquere(squares, weight, biggest)) {
            arr[0] = static_cast<jint>(biggest[0].x * ratio);
            arr[1] = static_cast<jint>(biggest[0].y * ratio);
            arr[2] = static_cast<jint>(biggest[1].x * ratio);
            arr[3] = static_cast<jint>(biggest[1].y * ratio);
            arr[4] = static_cast<jint>(biggest[2].x * ratio);
            arr[5] = static_cast<jint>(biggest[2].y * ratio);
            arr[6] = static_cast<jint>(biggest[3].x * ratio);
            arr[7] = static_cast<jint>(biggest[3].y * ratio);
        } else {
            arr[0] = static_cast<jint>(0);
            arr[1] = static_cast<jint>(0);
            arr[2] = static_cast<jint>(0);
            arr[3] = static_cast<jint>(rows - 1);
            arr[4] = static_cast<jint>(cols - 1);
            arr[5] = static_cast<jint>(rows - 1);
            arr[6] = static_cast<jint>(cols - 1);
            arr[7] = static_cast<jint>(0);
        }

    }
    env->ReleaseByteArrayElements(yuv420sp_, yuv420sp, 0);
    env->ReleaseIntArrayElements(arr_, arr, 0);
}