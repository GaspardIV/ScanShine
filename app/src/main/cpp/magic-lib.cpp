#include <opencv2/imgproc.hpp>
#include <jni.h>

std::vector<cv::Rect> detectLetters(const cv::Mat &img) {
    std::vector<cv::Rect> boundRect;
    cv::Mat workspace, element/*, img_sobel, img_threshold, ;*/;
    cvtColor(img, workspace, CV_BGR2GRAY);
    cv::Sobel(workspace, workspace, CV_8U, 1, 0, 3, 1, 0, cv::BORDER_DEFAULT);
    cv::threshold(workspace, workspace, 0, 255, CV_THRESH_OTSU+CV_THRESH_BINARY);
    element = getStructuringElement(cv::MORPH_RECT, cv::Size(17, 3) );
    cv::morphologyEx(workspace, workspace, CV_MOP_CLOSE, element);
    std::vector< std::vector< cv::Point> > contours;
    cv::findContours(workspace, contours, 0, 1);
    std::vector<std::vector<cv::Point> > contours_poly( contours.size() );
    for( int i = 0; i < contours.size(); i++ ) {
        if (contours[i].size() > 100) {
            cv::approxPolyDP(cv::Mat(contours[i]), contours_poly[i], 3, true);
            cv::Rect appRect(boundingRect(cv::Mat(contours_poly[i])));
            if (appRect.width > appRect.height) {
                boundRect.push_back(appRect);
            }
        }
    }
    return boundRect;
}


// https://stackoverflow.com/users/5878054/mainactual
void processRealMagic(cv::Mat &input) {
    int maxdim = input.cols; //std::max(input.rows,input.cols);
    const int dim = 1024;
    if ( maxdim > dim ) {
        double scale = (double)dim/(double)maxdim;
        cv::Mat t;
        cv::resize( input, input, cv::Size(), scale,scale );
    }

    cv::Mat localmax;
    cv::Mat kernel = cv::getStructuringElement(cv::MORPH_RECT,cv::Size(15,15) );

    cv::medianBlur(input,localmax,5);
    cv::morphologyEx(localmax, localmax, cv::MORPH_CLOSE, kernel, cv::Point(-1,-1), 1, cv::BORDER_REFLECT101);

    std::vector< cv::Rect > bb;
    // detectLetters by @William, modified to internally do the grayscale conversion if necessary
    // https://stackoverflow.com/questions/23506105/extracting-text-opencv?rq=1
    bb = detectLetters( input );
    // compose a simple Gaussian model for text background (still assumed white)

    cv::Mat mask( input.size(),CV_8UC1,cv::Scalar( 0 ) );
    if ( bb.empty() )
        return; // TODO; none found
    for (const auto &i : bb) {
        cv::rectangle( mask, i, cv::Scalar( 1 ), -1 );
    }
    cv::Mat mean,dev;
    cv::meanStdDev( localmax, mean, dev, mask );

    double minimum[3];
    double maximum[3];

    for ( unsigned int u=0;u<3;++u ) {
        minimum[u] = mean.at<double>(u ) - dev.at<double>( u );
        maximum[u] = mean.at<double>(u ) + dev.at<double>( u );
    }

    for ( int y=0;y<mask.rows;++y){
        for ( int x=0;x<mask.cols;++x){
            cv::Vec3b & col = localmax.at<cv::Vec3b>(y,x);
            for ( unsigned int u=0;u<3;++u )
            {
                if ( col[u]>maximum[u] )
                    col[u]=maximum[u];
                else if ( col[u]<minimum[u] )
                    col[u]=minimum[u];
            }
        }
    }

    for ( int y=0;y<input.rows;++y){
        for ( int x=0;x<input.cols;++x){
            const cv::Vec3b & v1=input.at<cv::Vec3b>(y,x);
            const cv::Vec3b & v2=localmax.at<cv::Vec3b>(y,x);
            cv::Vec3b & v3=input.at<cv::Vec3b>(y,x);
            for ( int i=0;i<3;++i )
            {
                double gain = 255.0/(double)v2[i];
                v3[i] = cv::saturate_cast<unsigned char>( gain * v1[i] );
            }
        }
    }
}

// https://stackoverflow.com/users/5878054/mainactual
void processRealMagicBW(cv::Mat &input) {
    cv::Mat localmax;
    cv::medianBlur(input,localmax,5);
// find local maximum
    cv::morphologyEx( localmax,localmax,
                      cv::MORPH_CLOSE,cv::getStructuringElement(cv::MORPH_RECT,cv::Size(15,15) ),
                      cv::Point(-1,-1),1,cv::BORDER_REFLECT101 );

// compute the per pixel gain such that the localmax goes to monochromatic 255
    for ( int y=0;y<input.rows;++y) {
        for ( int x=0;x<input.cols;++x) {
            const cv::Vec3b & v1=input.at<cv::Vec3b>(y,x);
            const cv::Vec3b & v2=localmax.at<cv::Vec3b>(y,x);
            cv::Vec3b & v3=input.at<cv::Vec3b>(y,x);
            for ( int i=0;i<3;++i ) {
                double gain = 255.0/(double)v2[i];
                v3[i] = cv::saturate_cast<unsigned char>( gain * v1[i] );
            }
        }
    }
}

extern "C" {
JNIEXPORT void JNICALL
Java_com_gaspard_scanshine_utils_EffectiveMagician_realMagic(JNIEnv *, jobject, jlong matAddr) {
    cv::Mat &imageMat = *(cv::Mat *) matAddr;
    processRealMagic(imageMat);
}

JNIEXPORT void JNICALL
Java_com_gaspard_scanshine_utils_EffectiveMagician_realMagicBW(JNIEnv *, jobject, jlong matAddr) {
    cv::Mat &imageMat = *(cv::Mat *) matAddr;
    processRealMagicBW(imageMat);
}

}

